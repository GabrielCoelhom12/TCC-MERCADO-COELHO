package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.ProjecaoReceita;
import br.com.coelho.escalas.dominio.TipoDia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjecaoReceitaRepository extends JpaRepository<ProjecaoReceita, Long> {
    Optional<ProjecaoReceita> findByTipoDia(TipoDia tipoDia);
}
