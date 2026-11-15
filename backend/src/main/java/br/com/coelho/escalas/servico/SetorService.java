package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.SetorRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Setores da loja e a demanda mínima de pessoas de cada um, por perfil de dia. */
@Service
public class SetorService {

    /** Laranja da identidade visual do Supermercado Coelho. */
    private static final String COR_PADRAO = "#F26A1B";

    /** clientesPorColaboradorHora vazio = setor com demanda fixa (não acompanha a previsão de movimento). */
    public record DadosSetor(@NotBlank String nome, String cor, Boolean ativo, Map<TipoDia, Integer> minimoPorDia,
                             @Min(1) @Max(500) Integer clientesPorColaboradorHora) {
    }

    private final SetorRepository setores;
    private final FuncionarioRepository funcionarios;

    public SetorService(SetorRepository setores, FuncionarioRepository funcionarios) {
        this.setores = setores;
        this.funcionarios = funcionarios;
    }

    @Transactional(readOnly = true)
    public List<Setor> listar() {
        return setores.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Setor criar(DadosSetor dados) {
        return setores.save(aplicar(new Setor(), dados));
    }

    @Transactional
    public Setor atualizar(Long id, DadosSetor dados) {
        return aplicar(setores.findById(id).orElseThrow(() -> new NaoEncontradoException("Setor", id)), dados);
    }

    @Transactional
    public void excluir(Long id) {
        if (funcionarios.existsBySetorId(id)) {
            throw new RegraNegocioException("O setor possui colaboradores. Transfira-os ou desative o setor.");
        }
        setores.deleteById(id);
    }

    private Setor aplicar(Setor s, DadosSetor dados) {
        s.setNome(dados.nome().trim());
        s.setCor(dados.cor() == null || dados.cor().isBlank() ? COR_PADRAO : dados.cor());
        s.setAtivo(dados.ativo() == null || dados.ativo());
        s.setClientesPorColaboradorHora(dados.clientesPorColaboradorHora());
        if (dados.minimoPorDia() != null) {
            dados.minimoPorDia().forEach((tipo, minimo) -> {
                if (minimo == null || minimo < 0) {
                    throw new RegraNegocioException("Demanda mínima inválida para " + tipo.getRotulo() + ".");
                }
            });
            s.getMinimoPorDia().clear();
            s.getMinimoPorDia().putAll(dados.minimoPorDia());
        }
        return s;
    }
}
