package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Mensagem;
import br.com.coelho.escalas.servico.ChatService;
import br.com.coelho.escalas.servico.ChatService.Contato;
import br.com.coelho.escalas.servico.ChatService.ConversaChat;
import br.com.coelho.escalas.servico.ChatService.MensagemChat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Chat entre usuários. Os eventos em tempo real saem pelo WebSocket (/ws, destino /user/queue/chat). */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record DiretaRequest(@NotNull Long usuarioId) {
    }

    public record GrupoRequest(@NotBlank @Size(max = ChatService.MAX_NOME_GRUPO) String nome, @NotNull List<Long> participantes) {
    }

    public record RenomearRequest(@NotBlank @Size(max = ChatService.MAX_NOME_GRUPO) String nome) {
    }

    public record ParticipantesRequest(@NotEmpty List<Long> usuarios) {
    }

    public record MensagemRequest(@Size(max = Mensagem.MAX_TEXTO) String texto, Long escalaId) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/usuarios")
    public List<Contato> contatos(Authentication auth) {
        return chat.contatos(auth.getName());
    }

    @GetMapping("/conversas")
    public List<ConversaChat> conversas(Authentication auth) {
        return chat.conversas(auth.getName());
    }

    @PostMapping("/conversas/diretas")
    public ConversaChat abrirDireta(@Valid @RequestBody DiretaRequest req, Authentication auth) {
        return chat.abrirDireta(auth.getName(), req.usuarioId());
    }

    @PostMapping("/conversas/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversaChat criarGrupo(@Valid @RequestBody GrupoRequest req, Authentication auth) {
        return chat.criarGrupo(auth.getName(), req.nome(), req.participantes());
    }

    @PutMapping("/conversas/{id}")
    public ConversaChat renomear(@PathVariable Long id, @Valid @RequestBody RenomearRequest req, Authentication auth) {
        return chat.renomear(auth.getName(), id, req.nome());
    }

    @PostMapping("/conversas/{id}/participantes")
    public ConversaChat adicionar(@PathVariable Long id, @Valid @RequestBody ParticipantesRequest req, Authentication auth) {
        return chat.adicionar(auth.getName(), id, req.usuarios());
    }

    @DeleteMapping("/conversas/{id}/participantes/eu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@PathVariable Long id, Authentication auth) {
        chat.sair(auth.getName(), id);
    }

    @GetMapping("/conversas/{id}/mensagens")
    public List<MensagemChat> mensagens(@PathVariable Long id, @RequestParam(required = false) Long antesDe,
                                       @RequestParam(required = false) Integer limite, Authentication auth) {
        return chat.mensagens(auth.getName(), id, antesDe, limite);
    }

    @PostMapping("/conversas/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemChat enviar(@PathVariable Long id, @Valid @RequestBody MensagemRequest req, Authentication auth) {
        return chat.enviar(auth.getName(), id, req.texto(), req.escalaId());
    }

    @PostMapping("/conversas/{id}/imagens")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemChat enviarImagem(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo,
                                     @RequestParam(required = false) String texto, Authentication auth) throws IOException {
        return chat.enviarImagem(auth.getName(), id, arquivo.getBytes(), texto);
    }

    @PostMapping("/conversas/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarLida(@PathVariable Long id, Authentication auth) {
        chat.marcarLida(auth.getName(), id);
    }

    /** Imagens nunca mudam: cache privado (só no navegador do usuário, nunca em proxies compartilhados). */
    @GetMapping("/mensagens/{id}/imagem")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(chat.imagem(auth.getName(), id));
    }
}
