package br.com.sidsidoso.arvoreb;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;

/**
 * Testes com um mini framework proprio (sem JUnit). Cada teste e um metodo
 * estatico registrado em {@link #main}; uma falha de assercao lanca
 * {@link AssertionError}. Sai com codigo 1 se algum teste falhar.
 */
public final class Testes {

    private static int passaram;
    private static final List<String> falhas = new ArrayList<>();

    private Testes() {
    }

    public static void main(String[] args) {
        teste("arvore nova esta vazia e valida", Testes::novaVazia);
        teste("ordem minima invalida", Testes::ordemInvalida);
        teste("rejeita chave e valor null", Testes::rejeitaNull);
        teste("min/max em arvore vazia lancam excecao", Testes::minMaxVazia);
        teste("inserir e buscar", Testes::inserirEBuscar);
        teste("inserir chave existente atualiza o valor", Testes::atualizar);
        teste("atualizar a mediana que sobe na divisao", Testes::atualizarMediana);
        teste("divisao da raiz aumenta a altura", Testes::divisaoDaRaiz);
        teste("sequencia do Cormen 18.2-1 (t = 2)", Testes::sequenciaCormen);
        teste("insercoes crescentes e decrescentes", Testes::insercoesOrdenadas);
        teste("altura respeita log_t((n+1)/2)", Testes::limiteDeAltura);
        teste("remocao em folha (caso 1)", Testes::remocaoCaso1);
        teste("remocao em no interno (casos 2a, 2b e 2c)", Testes::remocaoCaso2);
        teste("emprestimo e fusao na descida (casos 3a e 3b)", Testes::remocaoCaso3);
        teste("remover chave inexistente", Testes::removerInexistente);
        teste("remover tudo e reutilizar", Testes::removerTudo);
        teste("minimo e maximo", Testes::minimoEMaximo);
        teste("busca por intervalo", Testes::intervalo);
        teste("percurso em ordem e toString", Testes::percursoEmOrdem);
        teste("iterador vazio e fail-fast", Testes::iteradorFailFast);
        teste("impressao por niveis", Testes::impressaoPorNiveis);
        teste("validar detecta invariantes quebrados", Testes::validarDetecta);
        teste("chaves String com t = 3", Testes::chavesString);
        teste("aleatorio vs TreeMap, validando a cada operacao (t = 2, 3, 4)", Testes::aleatorioPequeno);
        teste("aleatorio grande vs TreeMap (t = 2, 5, 16, 50)", Testes::aleatorioGrande);

        System.out.println();
        if (!falhas.isEmpty()) {
            System.out.println(falhas.size() + " teste(s) falharam:");
            falhas.forEach(f -> System.out.println("  - " + f));
            System.out.println(passaram + " testes passaram");
            System.exit(1);
        }
        System.out.println(passaram + " testes passaram");
    }

    // ------------------------------------------------------------ mini framework

    private interface CorpoTeste {
        void executar() throws Exception;
    }

    private static void teste(String nome, CorpoTeste corpo) {
        try {
            corpo.executar();
            passaram++;
            System.out.println("[ OK ] " + nome);
        } catch (Throwable t) {
            falhas.add(nome + ": " + t);
            System.out.println("[FALHOU] " + nome + " -> " + t);
        }
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    private static void igual(Object esperado, Object obtido) {
        if (!Objects.equals(esperado, obtido)) {
            throw new AssertionError("esperado <" + esperado + "> mas foi <" + obtido + ">");
        }
    }

    private static <E extends Throwable> E lanca(Class<E> tipo, CorpoTeste corpo) {
        try {
            corpo.executar();
        } catch (Throwable t) {
            if (tipo.isInstance(t)) {
                return tipo.cast(t);
            }
            throw new AssertionError("esperava " + tipo.getSimpleName() + " mas foi " + t, t);
        }
        throw new AssertionError("esperava " + tipo.getSimpleName() + " mas nada foi lancado");
    }

    /** Arvore de caracteres com valor = posicao na sequencia; tambem coleta o rastro. */
    private static ArvoreB<Character, Integer> arvoreDe(int t, String sequencia) {
        ArvoreB<Character, Integer> a = new ArvoreB<>(t);
        for (int i = 0; i < sequencia.length(); i++) {
            a.inserir(sequencia.charAt(i), i);
        }
        a.validar();
        return a;
    }

    private static List<String> rastro(ArvoreB<?, ?> a) {
        List<String> passos = new ArrayList<>();
        a.definirRastreador(passos::add);
        return passos;
    }

    private static boolean algumComeca(List<String> passos, String prefixo) {
        return passos.stream().anyMatch(p -> p.startsWith(prefixo));
    }

    // ------------------------------------------------------------ casos

    private static void novaVazia() {
        ArvoreB<Integer, String> a = new ArvoreB<>(3);
        verificar(a.vazia(), "deveria estar vazia");
        igual(0, a.tamanho());
        igual(0, a.altura());
        igual(1, a.contarNos());
        igual(3, a.ordemMinima());
        igual(null, a.buscar(1));
        igual(null, a.remover(1));
        igual("{}", a.toString());
        a.validar();
    }

    private static void ordemInvalida() {
        lanca(IllegalArgumentException.class, () -> new ArvoreB<Integer, Integer>(1));
        lanca(IllegalArgumentException.class, () -> new ArvoreB<Integer, Integer>(0));
    }

    private static void rejeitaNull() {
        ArvoreB<Integer, String> a = new ArvoreB<>(2);
        lanca(NullPointerException.class, () -> a.inserir(null, "x"));
        lanca(NullPointerException.class, () -> a.inserir(1, null));
        lanca(NullPointerException.class, () -> a.buscar(null));
        lanca(NullPointerException.class, () -> a.remover(null));
        lanca(NullPointerException.class, () -> a.intervalo(null, 3));
        igual(0, a.tamanho());
    }

    private static void minMaxVazia() {
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        lanca(NoSuchElementException.class, a::minimo);
        lanca(NoSuchElementException.class, a::maximo);
    }

    private static void inserirEBuscar() {
        ArvoreB<Integer, String> a = new ArvoreB<>(2);
        for (int i = 0; i < 100; i += 2) {
            igual(null, a.inserir(i, "v" + i));
        }
        a.validar();
        igual(50, a.tamanho());
        for (int i = 0; i < 100; i++) {
            igual(i % 2 == 0 ? "v" + i : null, a.buscar(i));
            igual(i % 2 == 0, a.contem(i));
        }
    }

    private static void atualizar() {
        ArvoreB<Integer, String> a = new ArvoreB<>(2);
        for (int i = 0; i < 30; i++) {
            a.inserir(i, "a" + i);
        }
        for (int i = 0; i < 30; i++) {
            igual("a" + i, a.inserir(i, "b" + i));
        }
        a.validar();
        igual(30, a.tamanho());
        igual("b17", a.buscar(17));
    }

    private static void atualizarMediana() {
        // t = 2: [1 2 3] cheia como raiz; inserir(2) divide a raiz e 2 sobe
        ArvoreB<Integer, String> a = new ArvoreB<>(2);
        a.inserir(1, "um");
        a.inserir(2, "dois");
        a.inserir(3, "tres");
        igual("dois", a.inserir(2, "DOIS"));
        igual(3, a.tamanho());
        igual("DOIS", a.buscar(2));
        a.validar();
        // mesma situacao em um filho cheio (mediana sobe para um no interno)
        ArvoreB<Integer, String> b = new ArvoreB<>(2);
        for (int i = 1; i <= 5; i++) {
            b.inserir(i * 10, "v" + i);
        }
        igual("nivel 0: [20]\nnivel 1: [10] [30 40 50]\n", b.imprimirPorNiveis());
        igual("v4", b.inserir(40, "w4"));     // divide [30 40 50]; o proprio 40 sobe
        igual("nivel 0: [20 40]\nnivel 1: [10] [30] [50]\n", b.imprimirPorNiveis());
        igual("w4", b.buscar(40));
        igual(5, b.tamanho());
        b.validar();
    }

    private static void divisaoDaRaiz() {
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        a.inserir(1, 1);
        a.inserir(2, 2);
        a.inserir(3, 3);
        igual(0, a.altura());
        a.inserir(4, 4);
        igual(1, a.altura());
        igual("nivel 0: [2]\nnivel 1: [1] [3 4]\n", a.imprimirPorNiveis());
        a.validar();
    }

    private static void sequenciaCormen() {
        ArvoreB<Character, Integer> a = arvoreDe(2, "FSQKCLHTVWMRNPABXYDZE");
        igual("nivel 0: [K Q]\n"
                + "nivel 1: [B F] [M] [T W]\n"
                + "nivel 2: [A] [C D E] [H] [L] [N P] [R S] [V] [X Y Z]\n", a.imprimirPorNiveis());
    }

    private static void insercoesOrdenadas() {
        for (int t : new int[] {2, 3, 7}) {
            ArvoreB<Integer, Integer> crescente = new ArvoreB<>(t);
            ArvoreB<Integer, Integer> decrescente = new ArvoreB<>(t);
            for (int i = 0; i < 5_000; i++) {
                crescente.inserir(i, i);
                decrescente.inserir(4_999 - i, i);
            }
            crescente.validar();
            decrescente.validar();
            igual(crescente.chaves(), decrescente.chaves());
            igual(0, crescente.minimo());
            igual(4_999, decrescente.maximo());
        }
    }

    private static void limiteDeAltura() {
        Random rnd = new Random(5);
        for (int t : new int[] {2, 3, 10}) {
            ArvoreB<Integer, Integer> a = new ArvoreB<>(t);
            for (int i = 0; i < 20_000; i++) {
                a.inserir(rnd.nextInt(), i);
                if (a.tamanho() > 1) {
                    double limite = Math.log((a.tamanho() + 1) / 2.0) / Math.log(t);
                    verificar(a.altura() <= limite + 1e-9, "altura " + a.altura() + " > " + limite);
                }
            }
        }
    }

    private static void remocaoCaso1() {
        ArvoreB<Character, Integer> a = arvoreDe(3, "ABCDE");   // so a raiz-folha
        List<String> passos = rastro(a);
        igual(2, a.remover('C'));
        verificar(algumComeca(passos, "caso 1"), "esperava caso 1: " + passos);
        igual(List.of('A', 'B', 'D', 'E'), a.chaves());
        a.validar();
    }

    private static void remocaoCaso2() {
        // 2a: filho esquerdo com t chaves -> predecessor
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        for (int k : new int[] {10, 20, 30, 40, 5}) {
            a.inserir(k, k);                 // raiz [20], folhas [5 10] [30 40]
        }
        igual("nivel 0: [20]\nnivel 1: [5 10] [30 40]\n", a.imprimirPorNiveis());
        List<String> passos = rastro(a);
        igual(20, a.remover(20));
        verificar(algumComeca(passos, "caso 2a"), "esperava caso 2a: " + passos);
        igual("nivel 0: [10]\nnivel 1: [5] [30 40]\n", a.imprimirPorNiveis());
        a.validar();

        // 2b: esquerdo com t-1, direito com t -> sucessor
        passos.clear();
        igual(10, a.remover(10));
        verificar(algumComeca(passos, "caso 2b"), "esperava caso 2b: " + passos);
        igual("nivel 0: [30]\nnivel 1: [5] [40]\n", a.imprimirPorNiveis());
        a.validar();

        // 2c: ambos com t-1 -> fusao; a raiz esvazia e a altura diminui
        passos.clear();
        igual(30, a.remover(30));
        verificar(algumComeca(passos, "caso 2c"), "esperava caso 2c: " + passos);
        verificar(algumComeca(passos, "raiz ficou vazia"), "esperava reducao de altura: " + passos);
        igual("nivel 0: [5 40]\n", a.imprimirPorNiveis());
        igual(0, a.altura());
        a.validar();
    }

    private static void remocaoCaso3() {
        // 3a com irmao direito: raiz [20], folhas [10] [30 40]
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        for (int k : new int[] {10, 20, 30, 40}) {
            a.inserir(k, k);
        }
        List<String> passos = rastro(a);
        igual(10, a.remover(10));
        verificar(algumComeca(passos, "caso 3a"), "esperava caso 3a: " + passos);
        igual("nivel 0: [30]\nnivel 1: [20] [40]\n", a.imprimirPorNiveis());
        a.validar();

        // 3a com irmao esquerdo
        ArvoreB<Integer, Integer> b = new ArvoreB<>(2);
        for (int k : new int[] {10, 20, 30, 5}) {
            b.inserir(k, k);                 // raiz [20], folhas [5 10] [30]
        }
        passos = rastro(b);
        igual(30, b.remover(30));
        verificar(passos.stream().anyMatch(p -> p.contains("irmao esquerdo")), "esperava 3a esquerdo: " + passos);
        igual("nivel 0: [10]\nnivel 1: [5] [20]\n", b.imprimirPorNiveis());
        b.validar();

        // 3b: os dois irmaos com t-1 -> fusao
        passos.clear();
        igual(5, b.remover(5));
        verificar(algumComeca(passos, "caso 3b"), "esperava caso 3b: " + passos);
        igual("nivel 0: [10 20]\n", b.imprimirPorNiveis());
        b.validar();
    }

    private static void removerInexistente() {
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        for (int i = 0; i < 200; i += 2) {
            a.inserir(i, i);
        }
        for (int i = 1; i < 200; i += 2) {
            igual(null, a.remover(i));
            a.validar();                     // a descida pode reorganizar nos, mas sem quebrar nada
        }
        igual(100, a.tamanho());
    }

    private static void removerTudo() {
        for (int t : new int[] {2, 3, 6}) {
            ArvoreB<Integer, Integer> a = new ArvoreB<>(t);
            for (int i = 0; i < 1_000; i++) {
                a.inserir(i, -i);
            }
            for (int i = 0; i < 1_000; i++) {
                igual(-i, a.remover(i));
            }
            verificar(a.vazia(), "deveria ficar vazia");
            igual(0, a.altura());
            igual(1, a.contarNos());
            a.validar();
            a.inserir(42, 42);
            igual(42, a.buscar(42));
        }
    }

    private static void minimoEMaximo() {
        ArvoreB<Integer, Integer> a = new ArvoreB<>(3);
        Random rnd = new Random(9);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 2_000; i++) {
            int k = rnd.nextInt(1_000_000) - 500_000;
            a.inserir(k, k);
            min = Math.min(min, k);
            max = Math.max(max, k);
            igual(min, a.minimo());
            igual(max, a.maximo());
        }
    }

    private static void intervalo() {
        ArvoreB<Integer, String> a = new ArvoreB<>(2);
        for (int i = 0; i <= 100; i += 5) {
            a.inserir(i, "v" + i);
        }
        igual(List.of(Map.entry(20, "v20"), Map.entry(25, "v25"), Map.entry(30, "v30")), a.intervalo(18, 30));
        igual(List.of(Map.entry(0, "v0")), a.intervalo(-10, 0));
        igual(List.of(Map.entry(100, "v100")), a.intervalo(100, 1_000));
        igual(List.of(), a.intervalo(31, 34));
        igual(List.of(), a.intervalo(50, 40));
        igual(21, a.intervalo(Integer.MIN_VALUE, Integer.MAX_VALUE).size());
        igual(List.of(Map.entry(45, "v45")), a.intervalo(45, 45));
    }

    private static void percursoEmOrdem() {
        ArvoreB<Character, Integer> a = arvoreDe(2, "DBFACEG");
        igual(List.of('A', 'B', 'C', 'D', 'E', 'F', 'G'), a.chaves());
        igual("{A=3, B=1, C=4, D=0, E=5, F=2, G=6}", a.toString());
    }

    private static void iteradorFailFast() {
        Iterator<Map.Entry<Integer, Integer>> vazio = new ArvoreB<Integer, Integer>(2).iterator();
        verificar(!vazio.hasNext(), "iterador vazio nao tem proximo");
        lanca(NoSuchElementException.class, vazio::next);

        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        for (int i = 0; i < 10; i++) {
            a.inserir(i, i);
        }
        Iterator<Map.Entry<Integer, Integer>> it = a.iterator();
        it.next();
        a.inserir(100, 100);
        lanca(ConcurrentModificationException.class, it::next);

        Iterator<Map.Entry<Integer, Integer>> it2 = a.iterator();
        it2.next();
        a.remover(5);
        lanca(ConcurrentModificationException.class, it2::next);

        Iterator<Map.Entry<Integer, Integer>> it3 = a.iterator();
        a.inserir(0, 999);                   // so atualiza valor: nao e estrutural
        igual(Map.entry(0, 999), it3.next());
    }

    private static void impressaoPorNiveis() {
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        igual("nivel 0: []\n", a.imprimirPorNiveis());
        a.inserir(7, 7);
        igual("nivel 0: [7]\n", a.imprimirPorNiveis());
    }

    /** Corrompe a arvore por reflexao e confere que {@code validar()} percebe. */
    private static void validarDetecta() throws Exception {
        var campoRaiz = ArvoreB.class.getDeclaredField("raiz");
        campoRaiz.setAccessible(true);
        var campoTamanho = ArvoreB.class.getDeclaredField("tamanho");
        campoTamanho.setAccessible(true);

        // ordem quebrada
        ArvoreB<Integer, Integer> a = new ArvoreB<>(2);
        for (int i = 1; i <= 10; i++) {
            a.inserir(i, i);
        }
        Object raiz = campoRaiz.get(a);
        var campoChaves = raiz.getClass().getDeclaredField("chaves");
        campoChaves.setAccessible(true);
        Object[] chavesRaiz = (Object[]) campoChaves.get(raiz);
        Object original = chavesRaiz[0];
        chavesRaiz[0] = 1_000;
        lanca(IllegalStateException.class, a::validar);
        chavesRaiz[0] = original;
        a.validar();

        // contagem divergente
        campoTamanho.setInt(a, 11);
        lanca(IllegalStateException.class, a::validar);
        campoTamanho.setInt(a, 10);

        // no com chaves de menos (abaixo de t-1): esvazia uma folha
        var campoFilhos = raiz.getClass().getDeclaredField("filhos");
        var campoN = raiz.getClass().getDeclaredField("n");
        campoFilhos.setAccessible(true);
        campoN.setAccessible(true);
        var campoFolha = raiz.getClass().getDeclaredField("folha");
        campoFolha.setAccessible(true);
        Object folha = ((Object[]) campoFilhos.get(raiz))[0];
        while (!campoFolha.getBoolean(folha)) {
            folha = ((Object[]) campoFilhos.get(folha))[0];
        }
        int n = campoN.getInt(folha);
        campoN.setInt(folha, 0);
        lanca(IllegalStateException.class, a::validar);
        campoN.setInt(folha, n);
        a.validar();
    }

    private static void chavesString() {
        ArvoreB<String, Integer> a = new ArvoreB<>(3);
        String[] palavras = "pilha fila deque lista arvore grafo heap hash trie tabela vetor matriz conjunto mapa"
                .split(" ");
        for (int i = 0; i < palavras.length; i++) {
            a.inserir(palavras[i], palavras[i].length());
        }
        a.validar();
        igual("arvore", a.minimo());
        igual("vetor", a.maximo());
        igual(List.of("fila", "grafo", "hash", "heap"), a.intervalo("f", "i").stream().map(Map.Entry::getKey).toList());
        igual(5, a.remover("deque"));
        igual(null, a.buscar("deque"));
        a.validar();
    }

    /** Arvores pequenas e muitas colisoes de chave; {@code validar()} apos toda operacao. */
    private static void aleatorioPequeno() {
        Random rnd = new Random(2026);
        for (int t : new int[] {2, 3, 4}) {
            ArvoreB<Integer, Integer> arvore = new ArvoreB<>(t);
            TreeMap<Integer, Integer> oraculo = new TreeMap<>();
            for (int passo = 0; passo < 30_000; passo++) {
                // fases de crescimento e de esvaziamento
                boolean crescer = (passo / 2_000) % 2 == 0;
                int k = rnd.nextInt(400);
                int r = rnd.nextInt(100);
                if (r < (crescer ? 60 : 25)) {
                    igual(oraculo.put(k, passo), arvore.inserir(k, passo));
                } else if (r < 90) {
                    igual(oraculo.remove(k), arvore.remover(k));
                } else {
                    igual(oraculo.get(k), arvore.buscar(k));
                }
                arvore.validar();
                igual(oraculo.size(), arvore.tamanho());
                if (!oraculo.isEmpty()) {
                    igual(oraculo.firstKey(), arvore.minimo());
                    igual(oraculo.lastKey(), arvore.maximo());
                }
            }
            igual(new ArrayList<>(oraculo.keySet()), arvore.chaves());
        }
    }

    /** Arvores grandes; {@code validar()} e comparacoes completas em amostras. */
    private static void aleatorioGrande() {
        Random rnd = new Random(7);
        for (int t : new int[] {2, 5, 16, 50}) {
            ArvoreB<Integer, Integer> arvore = new ArvoreB<>(t);
            TreeMap<Integer, Integer> oraculo = new TreeMap<>();
            int universo = 200_000;
            for (int passo = 0; passo < 400_000; passo++) {
                boolean crescer = (passo / 50_000) % 2 == 0;
                int k = rnd.nextInt(universo);
                int r = rnd.nextInt(100);
                if (r < (crescer ? 65 : 30)) {
                    igual(oraculo.put(k, passo), arvore.inserir(k, passo));
                } else if (r < 92) {
                    igual(oraculo.remove(k), arvore.remover(k));
                } else if (r < 99) {
                    igual(oraculo.get(k), arvore.buscar(k));
                } else {
                    int ate = k + rnd.nextInt(2_000);
                    List<Map.Entry<Integer, Integer>> esperado = new ArrayList<>();
                    oraculo.subMap(k, true, ate, true).forEach((c, v) -> esperado.add(Map.entry(c, v)));
                    igual(esperado, arvore.intervalo(k, ate));
                }
                if (passo % 20_000 == 0) {
                    arvore.validar();
                    igual(oraculo.size(), arvore.tamanho());
                }
            }
            arvore.validar();
            List<Map.Entry<Integer, Integer>> esperado = new ArrayList<>();
            oraculo.forEach((c, v) -> esperado.add(Map.entry(c, v)));
            List<Map.Entry<Integer, Integer>> obtido = new ArrayList<>();
            arvore.forEach(obtido::add);
            igual(esperado, obtido);
        }
    }
}
