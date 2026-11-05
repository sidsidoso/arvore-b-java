package br.com.sidsidoso.arvoreb;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Demonstracao da arvore B: insercao, remocao com todos os casos, consultas e escala. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ArvoreB<Character, Integer> arvore = demoInsercao();
        demoConsultas(arvore);
        demoRemocao(arvore);
        demoEscala();
    }

    /** Sequencia do exercicio 18.2-1 do Cormen, com t = 2 (arvore 2-3-4). */
    private static ArvoreB<Character, Integer> demoInsercao() {
        titulo("1. Insercao com divisao proativa (t = 2: de 1 a 3 chaves por no)");
        ArvoreB<Character, Integer> arvore = new ArvoreB<>(2);
        arvore.definirRastreador(m -> System.out.println("     " + m));
        String sequencia = "FSQKCLHTVWMRNPABXYDZE";
        System.out.println("inserindo: " + String.join(" ", sequencia.split("")));
        for (int i = 0; i < sequencia.length(); i++) {
            char c = sequencia.charAt(i);
            System.out.println("  + " + c);
            arvore.inserir(c, i + 1);
        }
        arvore.definirRastreador(null);
        System.out.println();
        System.out.print(arvore.imprimirPorNiveis());
        System.out.printf("tamanho=%d altura=%d nos=%d%n", arvore.tamanho(), arvore.altura(), arvore.contarNos());
        arvore.validar();
        System.out.println("validar(): ok");
        return arvore;
    }

    private static void demoConsultas(ArvoreB<Character, Integer> arvore) {
        titulo("2. Consultas (valor = ordem de insercao)");
        System.out.println("buscar(K)        -> " + arvore.buscar('K'));
        System.out.println("buscar(G)        -> " + arvore.buscar('G'));
        System.out.println("minimo()         -> " + arvore.minimo());
        System.out.println("maximo()         -> " + arvore.maximo());
        System.out.println("intervalo(H, Q)  -> " + arvore.intervalo('H', 'Q'));
        StringBuilder emOrdem = new StringBuilder();
        for (Map.Entry<Character, Integer> e : arvore) {
            emOrdem.append(e.getKey());
        }
        System.out.println("percurso em ordem: " + emOrdem);
    }

    private static void demoRemocao(ArvoreB<Character, Integer> arvore) {
        titulo("3. Remocao (todos os casos do Cormen)");
        arvore.definirRastreador(m -> System.out.println("     " + m));
        for (char c : "FMGDBXSNLKPQTRV".toCharArray()) {
            System.out.println("  - " + c + (arvore.contem(c) ? "" : " (nao existe)"));
            arvore.remover(c);
            arvore.validar();
        }
        arvore.definirRastreador(null);
        System.out.println();
        System.out.print(arvore.imprimirPorNiveis());
        System.out.printf("tamanho=%d altura=%d; validar() apos cada remocao: ok%n", arvore.tamanho(), arvore.altura());
    }

    /** Altura e ocupacao com 1 milhao de chaves para varios t. */
    private static void demoEscala() {
        int n = 1_000_000;
        titulo("4. Escala: " + n + " chaves aleatorias distintas");
        int[] chaves = new int[n];
        for (int i = 0; i < n; i++) {
            chaves[i] = i;
        }
        Random rnd = new Random(42);
        for (int i = n - 1; i > 0; i--) { // embaralhamento de Fisher-Yates
            int j = rnd.nextInt(i + 1);
            int tmp = chaves[i];
            chaves[i] = chaves[j];
            chaves[j] = tmp;
        }
        System.out.printf("%5s  %6s  %14s  %9s  %9s  %s%n", "t", "altura", "altura maxima", "nos", "ocupacao", "apos remover metade");
        for (int t : new int[] {2, 4, 16, 64, 256}) {
            ArvoreB<Integer, Integer> arvore = new ArvoreB<>(t);
            for (int k : chaves) {
                arvore.inserir(k, k);
            }
            arvore.validar();
            int altura = arvore.altura();
            int nos = arvore.contarNos();
            double ocupacao = (double) n / ((long) nos * (2 * t - 1));
            int alturaMaxima = (int) Math.floor(Math.log((n + 1) / 2.0) / Math.log(t));
            for (int i = 0; i < n; i += 2) {
                arvore.remover(chaves[i]);
            }
            arvore.validar();
            System.out.printf(Locale.ROOT, "%5d  %6d  %14d  %9d  %8.1f%%  tamanho=%d altura=%d nos=%d%n",
                    t, altura, alturaMaxima, nos, ocupacao * 100, arvore.tamanho(), arvore.altura(), arvore.contarNos());
        }
        System.out.println("altura maxima = floor(log_t((n+1)/2)); ocupacao = chaves / (nos * (2t-1))");
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println(texto);
        System.out.println("=".repeat(texto.length()));
    }
}
