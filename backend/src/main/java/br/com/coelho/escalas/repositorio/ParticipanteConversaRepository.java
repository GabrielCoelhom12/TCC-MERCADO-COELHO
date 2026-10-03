package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.ParticipanteConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipanteConversaRepository extends JpaRepository<ParticipanteConversa, Long> {

    Optional<ParticipanteConversa> findByConversaIdAndUsuarioId(Long conversaId, Long usuarioId);

    List<ParticipanteConversa> findByUsuarioId(Long usuarioId);

    List<ParticipanteConversa> findByConversaIdIn(Collection<Long> conversaIds);

    List<ParticipanteConversa> findByConversaId(Long conversaId);

    /** [conversaId, quantidade] de mensagens de outros participantes ainda não lidas pelo usuário. */
    @Query("""
            select p.conversa.id, count(m) from ParticipanteConversa p, Mensagem m
            where p.usuario.id = :usuarioId and m.conversa = p.conversa and m.autor.id <> :usuarioId
              and m.id > coalesce(p.ultimaMensagemLidaId, 0)
            group by p.conversa.id""")
    List<Object[]> contarNaoLidas(Long usuarioId);
}
