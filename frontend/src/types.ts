export type TipoDia = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO' | 'FERIADO';
export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface Setor {
  id: number;
  nome: string;
  cor: string;
  ativo: boolean;
  minimoPorDia: Partial<Record<TipoDia, number>>;
  /** Clientes por hora que uma pessoa atende; vazio = demanda fixa (não acompanha o movimento). */
  clientesPorColaboradorHora: number | null;
}

export interface Funcionario {
  id: number;
  nome: string;
  matricula: string | null;
  cargo: string | null;
  setor: Setor;
  salarioMensal: number;
  cargaHorariaMensal: number;
  percentualEncargos: number;
  ativo: boolean;
  diasDisponiveis: DiaSemana[];
  custoHora: number;
  foto: string | null;
}

export type MotivoAusencia = 'FERIAS' | 'ATESTADO' | 'FOLGA_ACORDADA' | 'OUTRO';

export interface Ausencia {
  id: number;
  funcionarioId: number;
  funcionarioNome: string;
  inicio: string;
  fim: string;
  motivo: MotivoAusencia;
  observacao: string | null;
}

export type Aplicacao = 'DIAS_UTEIS' | 'DOMINGOS_FERIADOS' | 'TODOS';

export interface TurnoModelo {
  id: number;
  nome: string;
  sigla: string;
  horaInicio: string;
  horaFim: string;
  intervaloMinutos: number;
  aplicacao: Aplicacao;
  horas: number;
}

export interface Feriado {
  id: number;
  data: string;
  descricao: string;
  abrangencia: 'NACIONAL' | 'ESTADUAL' | 'MUNICIPAL';
  fatorCusto: number | null;
}

export interface ProjecaoData {
  id: number;
  data: string;
  valor: number;
  observacao: string | null;
}

export interface Projecoes {
  padrao: Record<TipoDia, number>;
  especificas: ProjecaoData[];
}

export interface Parametros {
  id: number;
  fatorDomingo: number;
  fatorFeriado: number;
  percentualTetoFolha: number;
  jornadaReferenciaHoras: number;
  modoFinanceiro: 'BLOQUEAR' | 'ALERTAR';
  jornadaNormalDiariaHoras: number;
  jornadaMaximaDiariaHoras: number;
  jornadaSemanalHoras: number;
  interjornadaMinimaHoras: number;
  maxDiasConsecutivos: number;
  maxDomingosConsecutivos: number;
  geracaoAutomatica: boolean;
  semanasAntecedencia: number;
}

export interface Escala {
  id: number;
  nome: string;
  dataInicio: string;
  dataFim: string;
  status: 'RASCUNHO' | 'APROVADA';
  observacao: string | null;
  criadaEm: string;
  aprovadaEm: string | null;
  aprovadaPor: string | null;
  geradaAutomaticamente: boolean;
}

export type Severidade = 'ERRO' | 'ALERTA';

export interface Violacao {
  severidade: Severidade;
  regra: string;
  data: string;
  funcionarioId: number | null;
  funcionarioNome: string | null;
  setorId: number | null;
  mensagem: string;
}

export type Situacao = 'OK' | 'ABAIXO_MINIMO' | 'ACIMA_LIMITE' | 'INVIAVEL' | 'VAZIO';

export interface ResumoSetor {
  setorId: number;
  nome: string;
  cor: string;
  operadores: number;
  minimo: number;
  custo: number;
  /** Cobertura das faixas de movimento (só nos setores que acompanham o movimento). */
  faixas: CoberturaFaixa[];
}

export interface CoberturaFaixa {
  inicio: string;
  fim: string;
  clientes: number;
  necessarios: number;
  alocados: number;
}

export interface ResumoDia {
  data: string;
  tipoDia: string;
  feriado: string | null;
  fator: number;
  receitaProjetada: number;
  tetoFolha: number;
  custoEscala: number;
  custoMedioOperador: number;
  operadores: number;
  lMin: number;
  lMax: number;
  situacao: Situacao;
  setores: ResumoSetor[];
  clientesPrevistos: number | null;
  pico: string | null;
}

export interface ResultadoValidacao {
  dias: ResumoDia[];
  violacoes: Violacao[];
  custoTotal: number;
  tetoTotal: number;
  erros: number;
  alertas: number;
  aprovavel: boolean;
}

export interface TurnoDTO {
  id: number;
  funcionarioId: number;
  data: string;
  horaInicio: string;
  horaFim: string;
  intervaloMinutos: number;
  rotulo: string;
  horas: number;
  custo: number;
}

export interface LinhaFuncionario {
  id: number;
  nome: string;
  cargo: string | null;
  setorId: number;
  setorNome: string;
  setorCor: string;
  custoHora: number;
  diasDisponiveis: DiaSemana[];
  ativo: boolean;
}

export interface Matriz {
  escala: Escala;
  funcionarios: LinhaFuncionario[];
  turnos: TurnoDTO[];
  ausencias: Ausencia[];
  modelos: TurnoModelo[];
  validacao: ResultadoValidacao;
}

export interface Deficit {
  data: string;
  setorId: number;
  setor: string;
  necessarios: number;
  alocados: number;
}

export interface Geracao {
  turnosCriados: number;
  deficits: Deficit[];
  matriz: Matriz;
}

export interface CapacidadeDia {
  data: string;
  tipoDia: TipoDia;
  feriado: string | null;
  fator: number;
  receitaProjetada: number;
  tetoFolha: number;
  custoMedioOperador: number;
  lMin: number;
  lMax: number;
  minimoPorSetor: Record<string, number>;
  inviavel: boolean;
}

export interface LinhaSimulacao {
  setorId: number;
  setor: string;
  minimo: number;
  proposto: number;
  recomendado: number;
  custoOperador: number;
  custoProposto: number;
  custoRecomendado: number;
}

export interface Simulacao {
  capacidade: CapacidadeDia;
  setores: LinhaSimulacao[];
  operadoresPropostos: number;
  custoProposto: number;
  operadoresRecomendados: number;
  custoRecomendado: number;
  economia: number;
  veredito: 'APROVADA' | 'SUPERDIMENSIONADA' | 'SUBDIMENSIONADA';
}

export type Perfil = 'ADMIN' | 'GESTOR' | 'SUPERVISOR';

export interface Usuario {
  id: number;
  login: string;
  nome: string;
  perfil: Perfil;
  ativo: boolean;
  foto: string | null;
}

// ---------------------------------------------------------------- chat

export interface Contato {
  id: number;
  nome: string;
  foto: string | null;
  online: boolean;
}

export interface MensagemChat {
  id: number;
  conversaId: number;
  autorId: number;
  autorNome: string;
  texto: string | null;
  /** URL protegida da imagem anexada (exige o token). */
  imagem: string | null;
  escala: { id: number; nome: string } | null;
  /** Registro automático de chamada de voz. */
  chamada: { resultado: 'ATENDIDA' | 'RECUSADA' | 'PERDIDA'; duracaoSegundos: number | null } | null;
  enviadaEm: string;
}

export interface ConversaChat {
  id: number;
  tipo: 'DIRETA' | 'GRUPO';
  nome: string;
  foto: string | null;
  participantes: Contato[];
  ultimaMensagem: MensagemChat | null;
  naoLidas: number;
  /** Última mensagem lida por participante (id do usuário → id da mensagem). */
  leituras: Record<string, number>;
  atualizadaEm: string;
}

export type StatusChamada = 'TOCANDO' | 'EM_ANDAMENTO' | 'ENCERRADA' | 'RECUSADA' | 'PERDIDA';

export interface Chamada {
  id: number;
  conversaId: number;
  chamador: Contato;
  destinatario: Contato;
  status: StatusChamada;
  iniciadaEm: string;
  atendidaEm: string | null;
  encerradaEm: string | null;
}

export type TipoSinal = 'offer' | 'answer' | 'ice';

export type EventoChat =
  | { tipo: 'MENSAGEM'; conversaId: number; mensagem: MensagemChat }
  | { tipo: 'CHAMADA'; conversaId: number; chamada: Chamada }
  | { tipo: 'SINAL'; chamadaId: number; sinal: { tipo: TipoSinal; dados: string } }
  | { tipo: 'CONVERSA'; conversaId: number; conversa: ConversaChat }
  | { tipo: 'SAIU'; conversaId: number }
  | { tipo: 'LEITURA'; conversaId: number; usuarioId: number; mensagemId: number };

// ---------------------------------------------------------------- movimento e previsão

export interface FaixaHoraria {
  id: number;
  inicio: string;
  fim: string;
}

export interface FaixaLancada {
  inicio: string;
  fim: string;
  clientes: number;
  vendas: number;
}

export interface Fechamento {
  data: string;
  faixas: FaixaLancada[];
  clientes: number;
  vendas: number;
  observacao: string | null;
  registradoPor: string | null;
  registradoEm: string | null;
}

export interface FaixaPrevista {
  inicio: string;
  fim: string;
  clientes: number;
}

export interface PrevisaoDia {
  data: string;
  vendas: number;
  clientes: number;
  faixas: FaixaPrevista[];
  amostras: number;
  explicacao: string[];
  pico: FaixaPrevista | null;
}

export interface DemandaSetor {
  setorId: number;
  nome: string;
  cor: string;
  minimoManual: number;
  necessarios: number;
  faixas: { inicio: string; fim: string; clientes: number; pessoas: number }[];
  turnosSugeridos: string[];
}

export interface PrevisaoDiaria {
  data: string;
  tipoDia: string;
  feriado: string | null;
  receitaProjetada: number;
  origemReceita: 'INFORMADA' | 'PREVISAO' | 'PADRAO';
  previsao: PrevisaoDia | null;
  setores: DemandaSetor[];
}
