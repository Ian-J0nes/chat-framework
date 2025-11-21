package org.marre.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.marre.user.entity.UserAuth;

@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {
}

