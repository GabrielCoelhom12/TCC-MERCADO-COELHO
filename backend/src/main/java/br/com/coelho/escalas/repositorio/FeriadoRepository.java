package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {
    List<Feriado> findAllByOrderByDataAsc();

    List<Feriado> findByDataBetween(LocalDate inicio, LocalDate fim);

    boolean existsByData(LocalDate data);
}
