package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.ProjecaoReceitaData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProjecaoReceitaDataRepository extends JpaRepository<ProjecaoReceitaData, Long> {
    List<ProjecaoReceitaData> findAllByOrderByDataAsc();

    List<ProjecaoReceitaData> findByDataBetween(LocalDate inicio, LocalDate fim);
}
