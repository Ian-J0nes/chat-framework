"""
RAG 服务：Chroma 向量库连接、文本切分、嵌入、查询
"""
from __future__ import annotations

import hashlib
import os
from functools import lru_cache
from typing import Any, Dict, List, Optional

import httpx
from loguru import logger
from openai import AsyncOpenAI


class RAGService:
    """RAG 服务：文本入库与相似度查询"""

    def __init__(self) -> None:
        self.collection_name: str = os.getenv("CHROMA_COLLECTION", "kb_default").strip()
        self._http = httpx.Client(timeout=15.0)
        self._rest_base_url: Optional[str] = None
        self._collection = None

        # Split params
        self.chunk_size: int = int(os.getenv("RAG_CHUNK_SIZE", "1000"))
        self.chunk_overlap: int = int(os.getenv("RAG_CHUNK_OVERLAP", "200"))

        # Embeddings
        self.emb_base_url = os.getenv("EMBEDDINGS_BASE_URL", "").strip()
        self.emb_api_key = os.getenv("EMBEDDINGS_API_KEY", "").strip()
        self.emb_model = os.getenv("EMBEDDINGS_MODEL", "BAAI/bge-m3").strip()
        self._emb_client = (
            AsyncOpenAI(api_key=self.emb_api_key, base_url=self.emb_base_url)
            if self.emb_api_key
            else None
        )

        # Connect REST
        base_url = self._detect_chroma_endpoint()
        if base_url:
            try:
                self._collection = self._get_or_create_collection_rest(base_url, self.collection_name)
                self._rest_base_url = base_url
                logger.info(f"Chroma REST 连接成功: {base_url}")
            except Exception as e:
                logger.error(f"Chroma REST init failed: {e}")
        else:
            logger.warning("Chroma REST 未探测到可用端点；RAG 将不可用")

    def _detect_chroma_endpoint(self) -> Optional[str]:
        """探测 Chroma REST 端点"""
        explicit = os.getenv("CHROMA_BASE_URL", "").strip()
        if explicit and self._probe(explicit):
            return explicit.rstrip("/")

        host = os.getenv("CHROMA_HOST", "127.0.0.1").strip() or "127.0.0.1"
        port = int(os.getenv("CHROMA_PORT", "8000"))
        ssl = os.getenv("CHROMA_SSL", "false").lower() == "true"
        scheme = "https" if ssl else "http"
        base = f"{scheme}://{host}:{port}"

        for cand in (f"{base}/api/v1", f"{base}/api", base):
            if self._probe(cand):
                return cand.rstrip("/")
        return None

    def _probe(self, base: str) -> bool:
        try:
            r = self._http.get(f"{base.rstrip('/')}/heartbeat", timeout=5.0)
            return r.status_code == 200
        except Exception:
            return False

    def _hash_id(self, base: str) -> str:
        return hashlib.sha256(base.encode("utf-8")).hexdigest()[:32]

    def _split_text(self, text: str) -> List[str]:
        if not text:
            return []
        chunks: List[str] = []
        step = max(1, self.chunk_size - self.chunk_overlap)
        for i in range(0, len(text), step):
            chunks.append(text[i : i + self.chunk_size])
        return chunks

    async def _embed(self, texts: List[str]) -> List[List[float]]:
        if not self._emb_client:
            raise RuntimeError("Embeddings client 未初始化 (EMBEDDINGS_*)")
        if not texts:
            return []
        resp = await self._emb_client.embeddings.create(model=self.emb_model, input=texts)
        return [item.embedding for item in resp.data]

    class _RestCollection:
        """Chroma REST Collection 封装"""

        def __init__(self, base_url: str, collection_id: str, client: httpx.Client) -> None:
            self.base_url = base_url.rstrip("/")
            self.id = collection_id
            self.client = client

        def upsert(
            self,
            *,
            ids: List[str],
            documents: List[str],
            metadatas: List[Dict[str, Any]],
            embeddings: List[List[float]],
        ) -> None:
            payload = {
                "ids": ids,
                "documents": documents,
                "metadatas": metadatas,
                "embeddings": embeddings,
            }
            r = self.client.post(f"{self.base_url}/collections/{self.id}/upsert", json=payload)
            r.raise_for_status()

        def query(
            self,
            *,
            query_embeddings: Optional[List[List[float]]] = None,
            query_texts: Optional[List[str]] = None,
            n_results: int = 5,
            where: Optional[Dict[str, Any]] = None,
        ) -> Dict[str, Any]:
            payload: Dict[str, Any] = {
                "n_results": n_results,
                "include": ["documents", "metadatas", "distances"],
            }
            if query_embeddings:
                payload["query_embeddings"] = query_embeddings
            elif query_texts:
                payload["query_texts"] = query_texts
            if where is not None:
                payload["where"] = where
            r = self.client.post(f"{self.base_url}/collections/{self.id}/query", json=payload)
            r.raise_for_status()
            return r.json()

    def _get_or_create_collection_rest(self, base_url: str, name: str):
        base = base_url.rstrip("/")

        def _extract_cols(payload: Any) -> List[Any]:
            if isinstance(payload, dict):
                inner = payload.get("collections") or payload.get("items") or payload.get("data")
                if isinstance(inner, list):
                    return inner
                return [payload]
            if isinstance(payload, list):
                return payload
            return []

        def _match(item: Any) -> Optional[str]:
            if isinstance(item, dict):
                nm = item.get("name") or item.get("collection_name")
                if nm == name:
                    return (
                        item.get("id")
                        or item.get("collection_id")
                        or (item.get("collection") or {}).get("id")
                    )
            return None

        try:
            r = self._http.get(f"{base}/collections")
            if r.status_code == 200:
                data = r.json()
                for c in _extract_cols(data):
                    cid = _match(c)
                    if cid:
                        return self._RestCollection(base, cid, self._http)
        except Exception:
            pass

        r = self._http.post(f"{base}/collections", json={"name": name})
        r.raise_for_status()
        try:
            created = r.json()
        except Exception:
            created = None
        if isinstance(created, dict):
            cid = (
                created.get("id")
                or (created.get("collection") or {}).get("id")
                or created.get("collection_id")
            )
            if cid:
                return self._RestCollection(base, cid, self._http)

        r2 = self._http.get(f"{base}/collections")
        if r2.status_code == 200:
            data2 = r2.json()
            for c in _extract_cols(data2):
                cid2 = _match(c)
                if cid2:
                    return self._RestCollection(base, cid2, self._http)
        raise RuntimeError(f"Cannot resolve collection id (base={base}, name={name})")

    async def ingest_text(
        self,
        *,
        text: str,
        doc_id: Optional[str] = None,
        namespace: Optional[str] = None,
        user_id: Optional[int] = None,
        tags: Optional[List[str]] = None,
        extra_metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """将文本切分、嵌入并入库"""
        if not self._collection:
            raise RuntimeError("Chroma 未连接或初始化失败")

        chunks = self._split_text(text)
        if not chunks:
            return {"doc_id": doc_id or "", "chunk_count": 0}

        embeddings = await self._embed(chunks)
        base_id = doc_id or self._hash_id((namespace or "default") + (str(user_id or "") + text[:64]))
        ids = [self._hash_id(f"{base_id}-{i}") for i in range(len(chunks))]

        metadatas: List[Dict[str, Any]] = []
        for idx in range(len(chunks)):
            md: Dict[str, Any] = {
                "namespace": namespace or "default",
                "chunk_index": idx,
                "doc_id": base_id,
            }
            if user_id is not None:
                md["user_id"] = user_id
            if tags:
                md["tags"] = tags
            if extra_metadata:
                md.update(extra_metadata)
            metadatas.append(md)

        self._collection.upsert(ids=ids, documents=chunks, metadatas=metadatas, embeddings=embeddings)
        return {"doc_id": base_id, "chunk_count": len(chunks)}

    async def query(
        self,
        *,
        query: str,
        top_k: int = 5,
        namespace: Optional[str] = None,
        user_id: Optional[int] = None,
        tags: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        """相似度查询"""
        if not self._collection:
            raise RuntimeError("Chroma 未连接或初始化失败")

        filters: Dict[str, Any] = {}
        if namespace:
            filters["namespace"] = namespace
        if user_id is not None:
            filters["user_id"] = user_id

        where: Optional[Dict[str, Any]] = None
        if filters:
            where = {"$and": [{k: v} for k, v in filters.items()]}

        q_emb = await self._embed([query])
        results = self._collection.query(query_embeddings=q_emb, n_results=top_k, where=where)

        out: List[Dict[str, Any]] = []
        ids = (results.get("ids") or [[None]])[0]
        docs = (results.get("documents") or [[""]])[0]
        scores = (results.get("distances") or [[None]])[0]
        metas = (results.get("metadatas") or [[None]])[0]

        for i in range(len(docs)):
            out.append(
                {
                    "id": ids[i] if i < len(ids) else None,
                    "content": docs[i],
                    "score": scores[i] if i < len(scores) else None,
                    "metadata": metas[i] if i < len(metas) else None,
                }
            )
        return {"results": out, "count": len(out)}


@lru_cache(maxsize=1)
def get_rag_service() -> RAGService:
    """获取 RAG 服务单例"""
    return RAGService()
