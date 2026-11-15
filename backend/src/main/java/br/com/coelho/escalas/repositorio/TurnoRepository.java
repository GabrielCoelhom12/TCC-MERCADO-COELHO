package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    List<Turno> findByEscalaIdOrderByDataAscHoraInicioAsc(Long escalaId);

    List<Turno> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<Turno> findByEscalaIdAndFuncionarioIdAndData(Long escalaId, Long funcionarioId, LocalDate data);

    boolean existsByFuncionario_Id(Long funcionarioId);

    /**
     * Remove os turnos um a um, pelo contexto de persistência (e não com um "delete" em lote direto no banco), para que
     * nenhuma entidade já carregada na transação continue apontando para um turno que deixou de existir.
     */
    void deleteByEscalaId(Long escalaId);
}
