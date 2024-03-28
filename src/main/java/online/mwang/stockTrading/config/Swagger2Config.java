package online.mwang.stockTrading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/9 15:48
 * @description: Swagger2Config
 */
@Configuration
@EnableSwagger2
public class Swagger2Config {

    @Bean
    public Docket userApi() {

        return new Docket(DocumentationType.SWAGGER_2)
                .enable(true)
                .apiInfo(new ApiInfoBuilder()
                        .title("接口文档")
                        .description("DESC")
                        .version("1.0")
                        .build())
                .select()
                .apis(RequestHandlerSelectors.basePackage("online.mwang"))
                .paths(PathSelectors.any())
                .build();
    }

}
