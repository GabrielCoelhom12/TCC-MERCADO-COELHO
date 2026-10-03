package br.com.coelho.escalas.api;

import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.servico.ChamadaService;
import br.com.coelho.escalas.servico.ChamadaService.ChamadaDTO;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.ServidoresIce;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Chamadas de voz. Os eventos (CHAMADA e SINAL) chegam pelo WebSocket do chat. */
@RestController
@RequestMapping("/api/chat")
public class ChamadaController {

    /** offer/answer (SDP) ou candidato ICE, serializados em JSON pelo navegador. */
    public record SinalRequest(@NotNull @Pattern(regexp = "offer|answer|ice") String tipo,
                               @NotNull @Size(max = 20_000) String dados) {
    }

    private final ChamadaService chamadas;
    private final ServidoresIce ice;
    private final UsuarioRepository usuarios;

    public ChamadaController(ChamadaService chamadas, ServidoresIce ice, UsuarioRepository usuarios) {
        this.chamadas = chamadas;
        this.ice = ice;
        this.usuarios = usuarios;
    }

    @PostMapping("/conversas/{id}/chamadas")
    @ResponseStatus(HttpStatus.CREATED)
    public ChamadaDTO iniciar(@PathVariable Long id, Authentication auth) {
        return chamadas.iniciar(auth.getName(), id);
    }

    @PostMapping("/chamadas/{id}/atender")
    public ChamadaDTO atender(@PathVariable Long id, Authentication auth) {
        return chamadas.atender(auth.getName(), id);
    }

    @PostMapping("/chamadas/{id}/recusar")
    public ChamadaDTO recusar(@PathVariable Long id, Authentication auth) {
        return chamadas.recusar(auth.getName(), id);
    }

    @PostMapping("/chamadas/{id}/encerrar")
    public ChamadaDTO encerrar(@PathVariable Long id, Authentication auth) {
        return chamadas.encerrar(auth.getName(), id);
    }

    @PostMapping("/chamadas/{id}/sinal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sinal(@PathVariable Long id, @Valid @RequestBody SinalRequest req, Authentication auth) {
        chamadas.sinalizar(auth.getName(), id, new ChamadaService.Sinal(req.tipo(), req.dados()));
    }

    @GetMapping("/chamadas/config")
    public ServidoresIce.Configuracao config(Authentication auth) {
        Long id = usuarios.findByLogin(auth.getName()).orElseThrow(() -> new NaoEncontradoException("Usuário", auth.getName())).getId();
        return ice.para(id);
    }
}
