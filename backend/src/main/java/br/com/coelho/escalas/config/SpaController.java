package br.com.coelho.escalas.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Encaminha as rotas do frontend (SPA) para o index.html quando o build é servido pelo próprio Spring. */
@Controller
public class SpaController {

    @GetMapping({"/{rota:^(?!ws$)[^.]*}", "/{rota:^(?!api$|ws$)[^.]*}/{sub:[^.]*}", "/{rota:^(?!api$|ws$)[^.]*}/{sub:[^.]*}/{x:[^.]*}"})
    public String encaminhar() {
        return "forward:/index.html";
    }
}
