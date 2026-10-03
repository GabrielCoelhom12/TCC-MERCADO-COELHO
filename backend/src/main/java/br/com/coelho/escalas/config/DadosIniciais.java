package br.com.coelho.escalas.config;

import br.com.coelho.escalas.dominio.*;
import br.com.coelho.escalas.repositorio.*;
import br.com.coelho.escalas.servico.CalendarioFeriados;
import br.com.coelho.escalas.servico.EscalaService;
import br.com.coelho.escalas.servico.MovimentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cenário HIPOTÉTICO do Supermercado Coelho (salários, projeções e demanda ilustrativos), carregado
 * apenas quando o banco está vazio. Serve para demonstrar o motor; os valores reais são informados
 * pela gerência nas telas de cadastro.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);
    private static final BigDecimal ENCARGOS = new BigDecimal("38.00");

    private final boolean habilitado;
    private final String senhaAdmin;
    private final UsuarioRepository usuarios;
    private final ParametrosRepository parametros;
    private final SetorRepository setores;
    private final FuncionarioRepository funcionarios;
    private final AusenciaRepository ausencias;
    private final TurnoModeloRepository modelos;
    private final FeriadoRepository feriados;
    private final ProjecaoReceitaRepository projecoes;
    private final MovimentoDiarioRepository movimentos;
    private final MovimentoService movimentoService;
    private final EscalaService escalas;
    private final PasswordEncoder encoder;

    public DadosIniciais(@Value("${app.seed.habilitado:true}") boolean habilitado,
                         @Value("${app.admin.senha:}") String senhaAdmin, UsuarioRepository usuarios,
                         ParametrosRepository parametros, SetorRepository setores, FuncionarioRepository funcionarios,
                         AusenciaRepository ausencias, TurnoModeloRepository modelos, FeriadoRepository feriados,
                         ProjecaoReceitaRepository projecoes, MovimentoDiarioRepository movimentos,
                         MovimentoService movimentoService, EscalaService escalas, PasswordEncoder encoder) {
        this.habilitado = habilitado;
        this.senhaAdmin = senhaAdmin;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.setores = setores;
        this.funcionarios = funcionarios;
        this.ausencias = ausencias;
        this.modelos = modelos;
        this.feriados = feriados;
        this.projecoes = projecoes;
        this.movimentos = movimentos;
        this.movimentoService = movimentoService;
        this.escalas = escalas;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (usuarios.count() > 0) {
            return;
        }
        boolean gerada = senhaAdmin == null || senhaAdmin.isBlank();
        String senha = gerada ? senhaAleatoria() : senhaAdmin;
        usuarios.save(new Usuario("gestor", encoder.encode(senha), "Gerência Supermercado Coelho", Usuario.Perfil.ADMIN));
        parametros.atuais();
        if (gerada) {
            log.warn("Usuário inicial criado: gestor / {} (senha gerada; defina APP_ADMIN_SENHA para escolher).", senha);
        } else {
            log.info("Usuário inicial criado: gestor (senha definida em APP_ADMIN_SENHA).");
        }
        if (!habilitado) {
            return;
        }

        Setor caixa = setor("Frente de Caixa", "#F26A1B", 4, 3, 4, 4, 5, 6, 3, 3);
        // A frente de caixa acompanha o movimento: cada operador atende ~30 clientes por hora.
        caixa.setClientesPorColaboradorHora(30);
        setores.save(caixa);
        Setor reposicao = setor("Reposição", "#8C5A2B", 3, 2, 3, 3, 3, 3, 1, 1);
        Setor acougue = setor("Açougue", "#C0392B", 2, 2, 2, 2, 2, 3, 1, 1);
        Setor padaria = setor("Padaria", "#E0A526", 2, 2, 2, 2, 2, 2, 1, 1);

        Funcionario ana = func("Ana Paula Souza", "C001", "Fiscal de caixa", caixa, "2300");
        func("Bruno Henrique Lima", "C002", "Operador de caixa", caixa, "1950");
        func("Camila Ferreira", "C003", "Operadora de caixa", caixa, "1950");
        Funcionario daniela = func("Daniela Martins", "C004", "Operadora de caixa", caixa, "1950");
        func("Eduardo Pereira", "C005", "Operador de caixa", caixa, "1950");
        func("Fernanda Rocha", "C006", "Operadora de caixa", caixa, "1950");
        Funcionario gabriela = func("Gabriela Alves", "C007", "Operadora de caixa", caixa, "1950");
        func("Heitor Gomes", "C008", "Operador de caixa", caixa, "1950");
        func("Igor Nascimento", "R001", "Encarregado de reposição", reposicao, "2400");
        func("Juliana Costa", "R002", "Repositora", reposicao, "1850");
        func("Kleber Ribeiro", "R003", "Repositor", reposicao, "1850");
        func("Larissa Mendes", "R004", "Repositora", reposicao, "1850");
        func("Marcos Vieira", "R005", "Repositor", reposicao, "1850");
        func("Natália Barbosa", "R006", "Repositora", reposicao, "1850");
        func("Otávio Carvalho", "A001", "Açougueiro", acougue, "2800");
        func("Paulo Teixeira", "A002", "Açougueiro", acougue, "2800");
        func("Rafael Moreira", "A003", "Auxiliar de açougue", acougue, "2100");
        func("Sérgio Duarte", "A004", "Auxiliar de açougue", acougue, "2100");
        func("Tatiane Freitas", "P001", "Padeira", padaria, "2600");
        func("Vinícius Araújo", "P002", "Padeiro", padaria, "2600");
        func("Wanessa Cardoso", "P003", "Auxiliar de padaria", padaria, "1900");
        func("Yasmin Correia", "P004", "Auxiliar de padaria", padaria, "1900");

        // Disponibilidade individual: estudantes sem disponibilidade em alguns dias.
        gabriela.setDiasDisponiveis(EnumSet.complementOf(EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)));
        daniela.setDiasDisponiveis(EnumSet.complementOf(EnumSet.of(DayOfWeek.SUNDAY)));
        funcionarios.save(gabriela);
        funcionarios.save(daniela);

        modelos.save(new TurnoModelo("Abertura", "ABE", LocalTime.of(7, 0), LocalTime.of(15, 20), 60, TurnoModelo.Aplicacao.DIAS_UTEIS));
        modelos.save(new TurnoModelo("Fechamento", "FEC", LocalTime.of(13, 40), LocalTime.of(22, 0), 60, TurnoModelo.Aplicacao.DIAS_UTEIS));
        modelos.save(new TurnoModelo("Intermediário", "INT", LocalTime.of(10, 0), LocalTime.of(18, 20), 60, TurnoModelo.Aplicacao.DIAS_UTEIS));
        modelos.save(new TurnoModelo("Domingo/Feriado", "DOM", LocalTime.of(8, 0), LocalTime.of(13, 45), 15, TurnoModelo.Aplicacao.DOMINGOS_FERIADOS));

        int ano = LocalDate.now().getYear();
        for (int a = ano; a <= ano + 1; a++) {
            for (Feriado f : CalendarioFeriados.doAno(a)) {
                if (!feriados.existsByData(f.getData())) {
                    feriados.save(f);
                }
            }
        }

        Map<TipoDia, String> receita = Map.of(
                TipoDia.SEGUNDA, "38000", TipoDia.TERCA, "30000", TipoDia.QUARTA, "34000", TipoDia.QUINTA, "36000",
                TipoDia.SEXTA, "48000", TipoDia.SABADO, "62000", TipoDia.DOMINGO, "21000", TipoDia.FERIADO, "24000");
        receita.forEach((tipo, valor) -> projecoes.save(new ProjecaoReceita(tipo, new BigDecimal(valor))));

        LocalDate hoje = LocalDate.now();
        movimentoService.faixas();
        historicoDeMovimento(hoje, receita);

        // Escala de demonstração para a próxima semana, gerada pelo próprio motor.
        LocalDate segunda = hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Ausencia ferias = new Ausencia();
        ferias.setFuncionario(ana);
        ferias.setInicio(segunda.plusDays(2));
        ferias.setFim(segunda.plusDays(15));
        ferias.setMotivo(Ausencia.Motivo.FERIAS);
        ausencias.save(ferias);

        Escala demo = escalas.criar(new EscalaService.EscalaRequest(null, segunda, segunda.plusDays(6),
                "Gerada automaticamente com o cenário de demonstração."));
        escalas.gerar(demo.getId());
        log.info("Cenário de demonstração do Supermercado Coelho carregado (escala {} a {}).", segunda, segunda.plusDays(6));
    }

    /** 16 caracteres atendendo à política de senha forte (um de cada classe garantido, posições embaralhadas). */
    static String senhaAleatoria() {
        String[] classes = {"ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnpqrstuvwxyz", "23456789", "@#$%&*!?"};
        String todos = String.join("", classes);
        SecureRandom rnd = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        for (String c : classes) {
            chars.add(c.charAt(rnd.nextInt(c.length())));
        }
        while (chars.size() < 16) {
            chars.add(todos.charAt(rnd.nextInt(todos.length())));
        }
        Collections.shuffle(chars, rnd);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    /**
     * 12 semanas de fechamentos HIPOTÉTICOS para a previsão ter o que aprender: o perfil do dia da semana segue as
     * projeções padrão, com início do mês (+12%), véspera de feriado (+25%), pico à tarde e variação aleatória.
     */
    private void historicoDeMovimento(LocalDate hoje, Map<TipoDia, String> receita) {
        Random aleatorio = new Random(42);
        Set<LocalDate> datasFeriado = feriados.findAll().stream().map(Feriado::getData).collect(Collectors.toSet());
        LocalTime[][] horarios = {
                {LocalTime.of(7, 0), LocalTime.of(10, 0)}, {LocalTime.of(10, 0), LocalTime.of(13, 0)},
                {LocalTime.of(13, 0), LocalTime.of(16, 0)}, {LocalTime.of(16, 0), LocalTime.of(19, 0)},
                {LocalTime.of(19, 0), LocalTime.of(22, 0)}};
        double[] diaUtil = {0.12, 0.20, 0.18, 0.30, 0.20};
        double[] sabado = {0.15, 0.28, 0.25, 0.22, 0.10};
        double[] domingoOuFeriado = {0.45, 0.55, 0, 0, 0};

        for (LocalDate d = hoje.minusWeeks(12); d.isBefore(hoje); d = d.plusDays(1)) {
            boolean feriado = datasFeriado.contains(d);
            TipoDia tipo = feriado ? TipoDia.FERIADO : TipoDia.de(d.getDayOfWeek());
            double vendas = Double.parseDouble(receita.get(tipo));
            if (!feriado && d.getDayOfMonth() <= 10) {
                vendas *= 1.12;
            }
            if (!feriado && datasFeriado.contains(d.plusDays(1))) {
                vendas *= 1.25;
            }
            vendas *= 1 + aleatorio.nextGaussian() * 0.05;
            int clientes = (int) Math.round(vendas / (58 + aleatorio.nextDouble() * 6));
            double[] perfil = feriado || d.getDayOfWeek() == DayOfWeek.SUNDAY ? domingoOuFeriado
                    : d.getDayOfWeek() == DayOfWeek.SATURDAY ? sabado : diaUtil;

            MovimentoDiario m = new MovimentoDiario(d);
            for (int i = 0; i < perfil.length; i++) {
                if (perfil[i] > 0) {
                    m.getFaixas().add(new MovimentoFaixa(horarios[i][0], horarios[i][1], (int) Math.round(clientes * perfil[i]),
                            BigDecimal.valueOf(vendas * perfil[i]).setScale(2, RoundingMode.HALF_UP)));
                }
            }
            m.setObservacao("Histórico de demonstração");
            m.setRegistradoPor("gestor");
            m.setRegistradoEm(d.atTime(22, 30));
            movimentos.save(m);
        }
    }

    private Setor setor(String nome, String cor, int seg, int ter, int qua, int qui, int sex, int sab, int dom, int fer) {
        Setor s = new Setor(nome, cor);
        int[] valores = {seg, ter, qua, qui, sex, sab, dom, fer};
        for (TipoDia t : TipoDia.values()) {
            s.getMinimoPorDia().put(t, valores[t.ordinal()]);
        }
        return setores.save(s);
    }

    private Funcionario func(String nome, String matricula, String cargo, Setor setor, String salario) {
        Funcionario f = new Funcionario();
        f.setNome(nome);
        f.setMatricula(matricula);
        f.setCargo(cargo);
        f.setSetor(setor);
        f.setSalarioMensal(new BigDecimal(salario));
        f.setCargaHorariaMensal(220);
        f.setPercentualEncargos(ENCARGOS);
        return funcionarios.save(f);
    }
}
