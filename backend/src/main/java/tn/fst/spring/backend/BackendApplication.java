package tn.fst.spring.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController   // ← important : transforme la classe en contrôleur REST
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Salut Ilef ! Ton Spring Boot marche bien ✓ (" + java.time.LocalDateTime.now() + ")";
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}