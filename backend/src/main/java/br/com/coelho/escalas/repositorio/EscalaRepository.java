package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Escala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface EscalaRepository extends JpaRepository<Escala, Long> {
    List<Escala> findAllByOrderByDataInicioDesc();

    @Query("select count(e) > 0 from Escala e where e.dataInicio <= :fim and e.dataFim >= :inicio and (:ignorarId is null or e.id <> :ignorarId)")
    boolean existeSobreposicao(LocalDate inicio, LocalDate fim, Long ignorarId);
}
