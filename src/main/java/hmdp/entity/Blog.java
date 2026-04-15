package hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_blog")
public class Blog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long userId;

    private String title;

    private String images;

    private String content;

    private Integer liked;

    private Integer comments;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /*
    -----不在数据库表中的字段---------
     */

    //用户昵称
    @TableField(exist = false)
    private String name;

    //用户头像
    @TableField(exist = false)
    private String icon;

    //是否点赞
    @TableField(exist = false)
    private Boolean isLike;
}
