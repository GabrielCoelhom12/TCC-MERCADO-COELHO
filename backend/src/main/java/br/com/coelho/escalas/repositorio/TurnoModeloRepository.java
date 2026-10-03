package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.TurnoModelo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurnoModeloRepository extends JpaRepository<TurnoModelo, Long> {
    List<TurnoModelo> findAllByOrderByHoraInicioAsc();
}
