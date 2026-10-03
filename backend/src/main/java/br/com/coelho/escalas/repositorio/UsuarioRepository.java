package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    List<Usuario> findAllByOrderByNomeAsc();

    boolean existsByLoginAndIdNot(String login, Long id);

    long countByPerfilAndAtivoTrue(Usuario.Perfil perfil);
}
