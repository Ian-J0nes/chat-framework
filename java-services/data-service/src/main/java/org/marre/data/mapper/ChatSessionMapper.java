package org.marre.data.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.marre.common.entity.ChatSession;

@Mapper
@TableName("chat_sessions")
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
