# 黑马点评

这是一个个人 SpringBoot 练手项目

## 技术栈

- Spring Boot
- Maven
- MySQL
- Redis

## 当前进度
- 4.8
  - 已完成项目初始化
  - 已完成首次上传到 GitHub
  - 已完成pom和yaml配置
  - 完成sql的数据导入到hmdp1
- 4.9
  - 补充需要用到的包
  - 实现了最基础的功能以测试连接是否顺利（通过id查找shop）
  - 为查找商铺的功能增加了Redis缓存（只完成了添加，未完成更新：先改数据库，再改缓存）
- 4.13
  - 实现了缓存情况下shop的更新操作
  - 增加了互斥锁
  - 测试了select和update功能
- 4.14
  - 实现了逻辑过期
  - 拆分ShopServiceImpl里的方法到工具类CacheClient中
  - 实现ShopType的整表查询
  - 补全User相关类并实现发送验证码
  - 实现登录验证
  - 增加了登录拦截器
  - 增加单日签到功能


## 进阶功能（暂时忽略）
- 忽略了RabbitMQ功能