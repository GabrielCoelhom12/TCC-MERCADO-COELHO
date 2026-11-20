package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.TrocaTurno;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TrocaTurnoRepository extends JpaRepository<TrocaTurno, Long> {

    @EntityGraph(attributePaths = {"titular", "substituto"})
    List<TrocaTurno> findByEscala_IdOrderByRegistradaEmDescIdDesc(Long escalaId);

    /** Trocas de um período, de qualquer escala (base para relatórios). */
    @EntityGraph(attributePaths = {"titular", "substituto"})
    List<TrocaTurno> findByDataBetweenOrderByDataAscHoraInicioAsc(LocalDate inicio, LocalDate fim);

    boolean existsByTitular_IdOrSubstituto_Id(Long titularId, Long substitutoId);
}
