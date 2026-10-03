package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Ausencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AusenciaRepository extends JpaRepository<Ausencia, Long> {
    List<Ausencia> findByFuncionario_IdOrderByInicioDesc(Long funcionarioId);

    List<Ausencia> findByFimGreaterThanEqualAndInicioLessThanEqual(LocalDate inicio, LocalDate fim);

    void deleteByFuncionario_Id(Long funcionarioId);
}
