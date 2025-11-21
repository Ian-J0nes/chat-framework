package org.marre.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.marre.common.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
