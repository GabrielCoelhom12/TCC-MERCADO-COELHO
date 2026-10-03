package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.ParametrosOperacionais;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametrosRepository extends JpaRepository<ParametrosOperacionais, Long> {

    default ParametrosOperacionais atuais() {
        return findById(ParametrosOperacionais.ID_UNICO).orElseGet(() -> save(new ParametrosOperacionais()));
    }
}
