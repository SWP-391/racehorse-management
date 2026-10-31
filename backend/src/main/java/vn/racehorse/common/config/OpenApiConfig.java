package vn.racehorse.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The API description shown on the documentation page; sign-in and the CSRF header are explained here (TECH-203). */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI racehorseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Quản lý ngựa đua - API")
                        .version("0.1.0")
                        .description("""
                                Đăng nhập bằng `POST /api/auth/login` (nhóm "Đăng nhập"); phiên làm việc nằm trong cookie, \
                                trình duyệt tự gửi lại. Các lệnh ghi (POST, PUT, PATCH, DELETE) cần header `X-XSRF-TOKEN` \
                                bằng giá trị cookie `XSRF-TOKEN`; trang này tự thêm header đó. \
                                Chưa đăng nhập thì mọi đường dẫn khác trả 401."""))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("csrf", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Giá trị cookie XSRF-TOKEN; trang tài liệu tự gửi.")));
    }
}
