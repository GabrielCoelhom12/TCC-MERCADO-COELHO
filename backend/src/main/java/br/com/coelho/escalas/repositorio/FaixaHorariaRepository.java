package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.FaixaHoraria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaixaHorariaRepository extends JpaRepository<FaixaHoraria, Long> {
    List<FaixaHoraria> findAllByOrderByInicioAsc();
}
