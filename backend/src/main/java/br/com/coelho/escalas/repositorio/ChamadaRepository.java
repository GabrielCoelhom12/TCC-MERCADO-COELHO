package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Chamada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ChamadaRepository extends JpaRepository<Chamada, Long> {

    @Query("select c from Chamada c where c.status in :status and (c.chamador.id = :usuarioId or c.destinatario.id = :usuarioId)")
    List<Chamada> doUsuarioComStatus(Long usuarioId, Collection<Chamada.Status> status);
}
