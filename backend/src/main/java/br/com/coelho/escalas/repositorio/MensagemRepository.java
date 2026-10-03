package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Mensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    /** Mensagens mais recentes primeiro, opcionalmente anteriores a um id (paginação por cursor). */
    @Query("select m from Mensagem m where m.conversa.id = :conversaId and (:antesDe is null or m.id < :antesDe) order by m.id desc")
    List<Mensagem> recentes(Long conversaId, Long antesDe, Pageable pagina);

    @Query("select m from Mensagem m where m.id in (select max(m2.id) from Mensagem m2 where m2.conversa.id in :conversaIds group by m2.conversa.id)")
    List<Mensagem> ultimasDe(Collection<Long> conversaIds);

    Optional<Mensagem> findTopByConversaIdOrderByIdDesc(Long conversaId);
}
