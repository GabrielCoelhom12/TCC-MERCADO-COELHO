import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import CampoSenha from './CampoSenha';

function Campo({ requisitos = false }: { requisitos?: boolean }) {
  const [valor, setValor] = useState('');
  return <CampoSenha rotulo="Senha" valor={valor} onMudar={setValor} mostrarRequisitos={requisitos} />;
}

const capsLock = (ligado: boolean) => ({ key: 'a', modifierCapsLock: ligado });

describe('CampoSenha', () => {
  it('avisa quando o Caps Lock está ligado e some ao desligar', () => {
    render(<Campo />);
    const input = screen.getByLabelText('Senha');

    fireEvent.keyDown(input, capsLock(true));
    expect(screen.getByRole('alert')).toHaveTextContent('Caps Lock ativado');

    fireEvent.keyUp(input, capsLock(false));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('esconde o aviso ao sair do campo', () => {
    render(<Campo />);
    const input = screen.getByLabelText('Senha');
    fireEvent.keyDown(input, capsLock(true));
    fireEvent.blur(input);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('mostra e oculta a senha', () => {
    render(<Campo />);
    const input = screen.getByLabelText('Senha');
    expect(input).toHaveAttribute('type', 'password');

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(input).toHaveAttribute('type', 'text');
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar senha' }));
    expect(input).toHaveAttribute('type', 'password');
  });

  it('marca os requisitos conforme a digitação', () => {
    render(<Campo requisitos />);
    const item = () => screen.getByText('Um caractere especial (ex.: @ # $ !)').closest('li')!;
    expect(item()).not.toHaveClass('ok');

    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'Senha@Forte1' } });
    expect(item()).toHaveClass('ok');
    expect(screen.getAllByRole('listitem').every((li) => li.classList.contains('ok'))).toBe(true);
  });
});
