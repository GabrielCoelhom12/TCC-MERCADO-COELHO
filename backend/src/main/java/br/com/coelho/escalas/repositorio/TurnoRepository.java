package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    List<Turno> findByEscalaIdOrderByDataAscHoraInicioAsc(Long escalaId);

    List<Turno> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<Turno> findByEscalaIdAndFuncionarioIdAndData(Long escalaId, Long funcionarioId, LocalDate data);

    boolean existsByFuncionario_Id(Long funcionarioId);

    @Modifying
    @Query("delete from Turno t where t.escala.id = :escalaId")
    void deleteByEscalaId(Long escalaId);
}
