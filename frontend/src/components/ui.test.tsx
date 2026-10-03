import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import type { Violacao } from '../types';
import { BadgeSituacao, Carregando, ListaViolacoes, Modal, ToastProvider, mensagemErro, useCarregar, useToast } from './ui';

describe('ui', () => {
  it('extrai a mensagem de erros conhecidos', () => {
    expect(mensagemErro(new ApiError('Falhou', 422))).toBe('Falhou');
    expect(mensagemErro(new Error('x'))).toBe('x');
    expect(mensagemErro('???')).toBe('Erro inesperado.');
  });

  it('mostra o toast e o remove depois de alguns segundos', () => {
    vi.useFakeTimers();
    function Botao() {
      const toast = useToast();
      return <button onClick={() => toast('ok', 'Salvo!')}>salvar</button>;
    }
    render(<ToastProvider><Botao /></ToastProvider>);

    fireEvent.click(screen.getByText('salvar'));
    expect(screen.getByRole('status')).toHaveTextContent('Salvo!');

    act(() => vi.advanceTimersByTime(5000));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('modal só aparece aberto e fecha com Esc ou clique fora', () => {
    const fechar = vi.fn();
    const { rerender } = render(<Modal titulo="Teste" aberto={false} onFechar={fechar}>conteúdo</Modal>);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    rerender(<Modal titulo="Teste" aberto onFechar={fechar} acoes={<button>OK</button>}>conteúdo</Modal>);
    expect(screen.getByRole('dialog', { name: 'Teste' })).toHaveTextContent('conteúdo');
    expect(screen.getByText('OK')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByRole('dialog'));
    expect(fechar).not.toHaveBeenCalled();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(fechar).toHaveBeenCalledTimes(1);
  });

  it('exibe estado de carregamento ou erro', () => {
    const { rerender } = render(<Carregando />);
    expect(screen.getByText('Carregando…')).toBeInTheDocument();
    rerender(<Carregando erro="Sem conexão" />);
    expect(screen.getByText('Sem conexão')).toBeInTheDocument();
  });

  it('useCarregar entrega dados, erro e permite recarregar', async () => {
    const carregar = vi.fn<() => Promise<string[]>>().mockRejectedValueOnce(new Error('falha')).mockResolvedValue(['a']);
    function Lista() {
      const { dados, erro, recarregar } = useCarregar(carregar);
      return <div><span>{erro ?? dados?.join(',')}</span><button onClick={recarregar}>de novo</button></div>;
    }
    render(<Lista />);

    expect(await screen.findByText('falha')).toBeInTheDocument();
    fireEvent.click(screen.getByText('de novo'));
    await waitFor(() => expect(screen.getByText('a')).toBeInTheDocument());
  });

  it('badge mostra a situação do dia', () => {
    const { rerender } = render(<BadgeSituacao situacao="ACIMA_LIMITE" />);
    expect(screen.getByText(/Superdimensionado/)).toHaveClass('erro');
    rerender(<BadgeSituacao situacao="OK" curto />);
    expect(screen.getByTitle('Dentro dos limites')).not.toHaveTextContent('Dentro');
  });

  it('lista violações ou mensagem de conformidade', () => {
    const v: Violacao = {
      severidade: 'ERRO', regra: 'INTERJORNADA', data: '2026-10-05', funcionarioId: 1,
      funcionarioNome: 'Ana', setorId: 1, mensagem: 'Descanso menor que 11h',
    };
    const { rerender } = render(<ListaViolacoes violacoes={[]} />);
    expect(screen.getByText(/em conformidade/)).toBeInTheDocument();

    rerender(<ListaViolacoes violacoes={[v, { ...v, severidade: 'ALERTA', regra: 'NOVA', funcionarioNome: null }]} />);
    expect(screen.getByText('05/10 · Interjornada · Ana')).toBeInTheDocument();
    expect(screen.getByText('05/10 · NOVA')).toBeInTheDocument();
    expect(screen.getByText('Alerta')).toHaveClass('alerta');
  });
});
