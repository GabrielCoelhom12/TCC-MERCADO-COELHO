package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Funcionario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {
    List<Funcionario> findAllByOrderBySetorNomeAscNomeAsc();

    boolean existsBySetorId(Long setorId);
}
