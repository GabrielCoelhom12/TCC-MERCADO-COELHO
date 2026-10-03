package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.MovimentoDiario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovimentoDiarioRepository extends JpaRepository<MovimentoDiario, Long> {
    List<MovimentoDiario> findByDataBetweenOrderByDataDesc(LocalDate inicio, LocalDate fim);

    Optional<MovimentoDiario> findByData(LocalDate data);
}
