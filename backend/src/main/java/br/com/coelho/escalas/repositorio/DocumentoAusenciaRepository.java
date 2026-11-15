package br.com.coelho.escalas.repositorio;

import br.com.coelho.escalas.dominio.DocumentoAusencia;
import br.com.coelho.escalas.dominio.DocumentoResumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface DocumentoAusenciaRepository extends JpaRepository<DocumentoAusencia, Long> {

    /** Lista sem carregar o conteúdo dos arquivos. */
    @Query("""
            select new br.com.coelho.escalas.dominio.DocumentoResumo(d.id, d.ausencia.id, d.nomeArquivo, d.tipo, d.tamanho,
                   d.enviadoEm, d.enviadoPor)
            from DocumentoAusencia d where d.ausencia.id = :ausenciaId order by d.enviadoEm, d.id""")
    List<DocumentoResumo> resumos(Long ausenciaId);

    /** Pares [id da ausência, quantidade de documentos]. */
    @Query("select d.ausencia.id, count(d) from DocumentoAusencia d where d.ausencia.id in :ausencias group by d.ausencia.id")
    List<Object[]> contarPorAusencia(Collection<Long> ausencias);

    long countByAusencia_Id(Long ausenciaId);

    void deleteByAusencia_Id(Long ausenciaId);
}
