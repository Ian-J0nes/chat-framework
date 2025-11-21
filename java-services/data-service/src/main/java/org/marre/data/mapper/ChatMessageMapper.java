package org.marre.data.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.marre.common.entity.ChatMessage;

@Mapper
@TableName("chat_messages")
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
