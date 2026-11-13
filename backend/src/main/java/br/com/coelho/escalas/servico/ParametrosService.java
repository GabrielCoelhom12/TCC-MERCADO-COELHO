package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.ParametrosOperacionais;
import br.com.coelho.escalas.repositorio.ParametrosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Parâmetros que regem o motor (fatores de calendário, teto da folha e limites da CLT). */
@Service
public class ParametrosService {

    private static final int MAX_SEMANAS_ANTECEDENCIA = 4;

    private final ParametrosRepository parametros;

    public ParametrosService(ParametrosRepository parametros) {
        this.parametros = parametros;
    }

    /** Na primeira leitura, grava os valores padrão. */
    @Transactional
    public ParametrosOperacionais obter() {
        return parametros.atuais();
    }

    @Transactional
    public ParametrosOperacionais salvar(ParametrosOperacionais novos) {
        validar(novos);
        novos.setId(ParametrosOperacionais.ID_UNICO);
        if (novos.getModoFinanceiro() == null) {
            novos.setModoFinanceiro(ParametrosOperacionais.ModoFinanceiro.BLOQUEAR);
        }
        return parametros.save(novos);
    }

    private static void validar(ParametrosOperacionais p) {
        exigirMinimo(p.getFatorDomingo(), "1", "Fator de domingo deve ser ≥ 1,0.");
        exigirMinimo(p.getFatorFeriado(), "1", "Fator de feriado deve ser ≥ 1,0.");
        exigirMinimo(p.getPercentualTetoFolha(), "0.01", "O teto da folha deve ser maior que zero.");
        exigirMinimo(p.getJornadaReferenciaHoras(), "0.5", "Jornada de referência inválida.");
        exigirMinimo(p.getJornadaNormalDiariaHoras(), "1", "Jornada normal diária inválida.");
        exigirMinimo(p.getJornadaMaximaDiariaHoras(), "1", "Jornada máxima diária inválida.");
        exigirMinimo(p.getJornadaSemanalHoras(), "1", "Jornada semanal inválida.");
        exigirMinimo(p.getInterjornadaMinimaHoras(), "0", "Interjornada inválida.");
        if (p.getSemanasAntecedencia() < 1 || p.getSemanasAntecedencia() > MAX_SEMANAS_ANTECEDENCIA) {
            throw new RegraNegocioException("A geração automática pode criar de 1 a " + MAX_SEMANAS_ANTECEDENCIA + " semanas à frente.");
        }
        if (p.getMaxDiasConsecutivos() < 1 || p.getMaxDomingosConsecutivos() < 0) {
            throw new RegraNegocioException("Limites de dias/domingos consecutivos inválidos.");
        }
        if (p.getJornadaMaximaDiariaHoras().compareTo(p.getJornadaNormalDiariaHoras()) < 0) {
            throw new RegraNegocioException("A jornada máxima não pode ser menor que a normal.");
        }
    }

    private static void exigirMinimo(BigDecimal valor, String minimo, String mensagem) {
        if (valor == null || valor.compareTo(new BigDecimal(minimo)) < 0) {
            throw new RegraNegocioException(mensagem);
        }
    }
}
