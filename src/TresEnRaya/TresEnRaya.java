package TresEnRaya;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class TresEnRaya extends JFrame {
    private JButton[] botones = new JButton[9];
    private JLabel labelTurno = new JLabel("Turno de: Jugador", SwingConstants.CENTER);
    private char[] tablero = new char[9];
    private Logica logica = new Logica();
    private boolean juegoTerminado = false;

    public TresEnRaya() {
        setTitle("Tres en Raya");
        setSize(500, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        add(labelTurno, BorderLayout.NORTH);

        JPanel panel = new JPanel(new GridLayout(3, 3, 5, 5));
        panel.setBackground(Color.BLACK);
        for (int i = 0; i < 9; i++) {
            int pos = i;
            botones[i] = new JButton("");
            botones[i].setFont(new Font("Arial", Font.BOLD, 60));
            botones[i].addActionListener(e -> manejarClic(pos));
            panel.add(botones[i]);
        }
        add(panel, BorderLayout.CENTER);

        JButton btnReset = new JButton("Reiniciar");
        btnReset.addActionListener(e -> reiniciar());
        add(btnReset, BorderLayout.SOUTH);

        reiniciar();
    }

    private void manejarClic(int pos) {
        if (juegoTerminado || tablero[pos] != ' ') return;

        marcar(pos, 'X', Color.RED);
        if (verificarFin('X', "¡Ganaste!")) return;

        int pcPos = logica.mejorJugada(tablero);
        if (pcPos != -1) {
            marcar(pcPos, 'O', Color.BLUE);
            verificarFin('O', "¡Gana la PC!");
        }
    }

    private void marcar(int pos, char jugador, Color color) {
        tablero[pos] = jugador;
        botones[pos].setText(String.valueOf(jugador));
        botones[pos].setForeground(color);
    }

    private boolean verificarFin(char jugador, String mensaje) {
        if (logica.hayGanador(tablero, jugador)) {
            labelTurno.setText(mensaje);
            return juegoTerminado = true;
        }
        if (logica.tableroLleno(tablero)) {
            labelTurno.setText("¡Empate!");
            return juegoTerminado = true;
        }
        return false;
    }

    private void reiniciar() {
        Arrays.fill(tablero, ' ');
        juegoTerminado = false;
        for (JButton b : botones) b.setText("");
        labelTurno.setText("Turno de: Jugador");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TresEnRaya().setVisible(true));
    }
}