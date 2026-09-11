package TresEnRaya;

public class Logica {
    private static final int[][] COMBOS = {
            {0,1,2}, {3,4,5}, {6,7,8}, {0,3,6}, {1,4,7}, {2,5,8}, {0,4,8}, {2,4,6}
    };

    public boolean hayGanador(char[] t, char j) {
        for (int[] c : COMBOS) if (t[c[0]] == j && t[c[1]] == j && t[c[2]] == j) return true;
        return false;
    }

    public boolean tableroLleno(char[] t) {
        for (char c : t) if (c == ' ') return false;
        return true;
    }

    private int minimax(char[] t, boolean esPC) {
        if (hayGanador(t, 'O')) return 1;
        if (hayGanador(t, 'X')) return -1;
        if (tableroLleno(t)) return 0;

        int mejor = esPC ? -100 : 100;
        for (int i = 0; i < 9; i++) {
            if (t[i] == ' ') {
                t[i] = esPC ? 'O' : 'X';
                int val = minimax(t, !esPC);
                t[i] = ' ';
                mejor = esPC ? Math.max(mejor, val) : Math.min(mejor, val);
            }
        }
        return mejor;
    }

    public int mejorJugada(char[] t) {
        int mejorVal = -100, pos = -1;
        for (int i = 0; i < 9; i++) {
            if (t[i] == ' ') {
                t[i] = 'O';
                int val = minimax(t, false);
                t[i] = ' ';
                if (val > mejorVal) { mejorVal = val; pos = i; }
            }
        }
        return pos;
    }
}