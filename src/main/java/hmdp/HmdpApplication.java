package hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("hmdp.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class HmdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmdpApplication.class, args);
    }

}
