package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.ParametrosOperacionais;
import br.com.coelho.escalas.repositorio.ParametrosRepository;
import br.com.coelho.escalas.servico.RegraNegocioException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/parametros")
public class ParametrosController {

    private final ParametrosRepository parametros;

    public ParametrosController(ParametrosRepository parametros) {
        this.parametros = parametros;
    }

    @GetMapping
    public ParametrosOperacionais obter() {
        return parametros.atuais();
    }

    @PutMapping
    @Transactional
    public ParametrosOperacionais salvar(@RequestBody ParametrosOperacionais req) {
        exigir(req.getFatorDomingo(), "1", "Fator de domingo deve ser ≥ 1,0.");
        exigir(req.getFatorFeriado(), "1", "Fator de feriado deve ser ≥ 1,0.");
        exigir(req.getPercentualTetoFolha(), "0.01", "O teto da folha deve ser maior que zero.");
        exigir(req.getJornadaReferenciaHoras(), "0.5", "Jornada de referência inválida.");
        exigir(req.getJornadaNormalDiariaHoras(), "1", "Jornada normal diária inválida.");
        exigir(req.getJornadaMaximaDiariaHoras(), "1", "Jornada máxima diária inválida.");
        exigir(req.getJornadaSemanalHoras(), "1", "Jornada semanal inválida.");
        exigir(req.getInterjornadaMinimaHoras(), "0", "Interjornada inválida.");
        if (req.getSemanasAntecedencia() < 1 || req.getSemanasAntecedencia() > 4) {
            throw new RegraNegocioException("A geração automática pode criar de 1 a 4 semanas à frente.");
        }
        if (req.getMaxDiasConsecutivos() < 1 || req.getMaxDomingosConsecutivos() < 0) {
            throw new RegraNegocioException("Limites de dias/domingos consecutivos inválidos.");
        }
        if (req.getJornadaMaximaDiariaHoras().compareTo(req.getJornadaNormalDiariaHoras()) < 0) {
            throw new RegraNegocioException("A jornada máxima não pode ser menor que a normal.");
        }
        req.setId(ParametrosOperacionais.ID_UNICO);
        if (req.getModoFinanceiro() == null) {
            req.setModoFinanceiro(ParametrosOperacionais.ModoFinanceiro.BLOQUEAR);
        }
        return parametros.save(req);
    }

    private static void exigir(BigDecimal valor, String minimo, String mensagem) {
        if (valor == null || valor.compareTo(new BigDecimal(minimo)) < 0) {
            throw new RegraNegocioException(mensagem);
        }
    }
}
