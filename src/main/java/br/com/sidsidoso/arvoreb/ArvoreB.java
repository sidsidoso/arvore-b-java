package br.com.sidsidoso.arvoreb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Arvore B de ordem minima {@code t} (definicao do Cormen), mapeando chaves
 * {@link Comparable} para valores.
 *
 * <p>Invariantes:
 * <ul>
 *   <li>todo no tem no maximo {@code 2t - 1} chaves e, exceto a raiz, no
 *       minimo {@code t - 1};</li>
 *   <li>um no interno com {@code n} chaves tem exatamente {@code n + 1} filhos,
 *       e as chaves separam os intervalos dos filhos;</li>
 *   <li>todas as folhas estao no mesmo nivel.</li>
 * </ul>
 * Por isso a altura e no maximo {@code log_t((n + 1) / 2)} e buscar, inserir e
 * remover custam O(t * log_t n) comparacoes (O(log n) com busca binaria dentro
 * do no) e tocam O(log_t n) nos.
 *
 * <p>A insercao divide proativamente todo no cheio encontrado na descida, e a
 * remocao garante que todo no em que desce tem pelo menos {@code t} chaves
 * (emprestando de um irmao ou fundindo); assim ambas fazem uma unica passada
 * da raiz para baixo, sem precisar voltar.
 *
 * <p>Nao aceita chaves nem valores {@code null}. Nao e thread-safe.
 *
 * @param <K> tipo das chaves
 * @param <V> tipo dos valores
 */
public final class ArvoreB<K extends Comparable<? super K>, V> implements Iterable<Map.Entry<K, V>> {

    /** No da arvore. Os vetores tem tamanho fixo; so as primeiras {@code n} posicoes sao validas. */
    private static final class No {
        int n;
        final boolean folha;
        final Object[] chaves;
        final Object[] valores;
        /** {@code null} nas folhas. */
        final No[] filhos;

        No(int t, boolean folha) {
            this.folha = folha;
            this.chaves = new Object[2 * t - 1];
            this.valores = new Object[2 * t - 1];
            this.filhos = folha ? null : new No[2 * t];
        }
    }

    private final int t;
    private No raiz;
    private int tamanho;
    /** Contador de modificacoes estruturais, usado pelo iterador fail-fast. */
    private int modificacoes;
    /** Recebe a descricao de cada passo de insercao/remocao (para fins didaticos). */
    private Consumer<String> rastreador;

    /**
     * Cria uma arvore vazia.
     *
     * @param ordemMinima {@code t >= 2}; cada no guarda entre {@code t - 1} e {@code 2t - 1} chaves
     * @throws IllegalArgumentException se {@code t < 2}
     */
    public ArvoreB(int ordemMinima) {
        if (ordemMinima < 2) {
            throw new IllegalArgumentException("a ordem minima deve ser pelo menos 2: " + ordemMinima);
        }
        this.t = ordemMinima;
        this.raiz = new No(t, true);
    }

    /**
     * Define um rastreador que recebe uma descricao textual de cada divisao,
     * emprestimo, fusao e caso de remocao. Passe {@code null} para desligar.
     *
     * @param rastreador consumidor das mensagens
     */
    public void definirRastreador(Consumer<String> rastreador) {
        this.rastreador = rastreador;
    }

    // ------------------------------------------------------------ busca

    /**
     * Busca o valor associado a chave.
     *
     * @param chave chave procurada (nao nula)
     * @return valor, ou {@code null} se a chave nao existir
     */
    public V buscar(K chave) {
        exigirNaoNulo(chave, "chave nula");
        No x = raiz;
        while (true) {
            int i = posicao(x, chave);
            if (i < x.n && comparar(chave, x.chaves[i]) == 0) {
                return valor(x, i);
            }
            if (x.folha) {
                return null;
            }
            x = x.filhos[i];
        }
    }

    /**
     * @param chave chave procurada (nao nula)
     * @return {@code true} se a chave existir
     */
    public boolean contem(K chave) {
        return buscar(chave) != null;
    }

    // ------------------------------------------------------------ insercao

    /**
     * Insere a chave com o valor, ou atualiza o valor se a chave ja existir.
     * Faz uma unica descida: todo no cheio no caminho e dividido antes de se
     * descer nele, entao sempre ha espaco para a chave subir de um filho.
     *
     * @param chave chave (nao nula)
     * @param valor valor (nao nulo)
     * @return valor anterior, ou {@code null} se a chave era nova
     */
    public V inserir(K chave, V valor) {
        exigirNaoNulo(chave, "chave nula");
        exigirNaoNulo(valor, "valor nulo");
        if (raiz.n == 2 * t - 1) {
            No novaRaiz = new No(t, false);
            novaRaiz.filhos[0] = raiz;
            raiz = novaRaiz;
            rastrear(() -> "raiz cheia: nova raiz criada (altura aumenta)");
            dividirFilho(novaRaiz, 0);
        }
        No x = raiz;
        while (true) {
            int i = posicao(x, chave);
            if (i < x.n && comparar(chave, x.chaves[i]) == 0) {
                return substituirValor(x, i, valor);
            }
            if (x.folha) {
                deslocarDireita(x, i);
                x.chaves[i] = chave;
                x.valores[i] = valor;
                x.n++;
                tamanho++;
                modificacoes++;
                return null;
            }
            if (x.filhos[i].n == 2 * t - 1) {
                dividirFilho(x, i);
                int c = comparar(chave, x.chaves[i]);
                if (c == 0) {
                    return substituirValor(x, i, valor); // a chave era a mediana que subiu
                }
                if (c > 0) {
                    i++;
                }
            }
            x = x.filhos[i];
        }
    }

    /**
     * Divide o filho cheio {@code x.filhos[i]} (com {@code 2t - 1} chaves) em
     * dois nos de {@code t - 1} chaves; a mediana sobe para {@code x}.
     */
    private void dividirFilho(No x, int i) {
        No y = x.filhos[i];
        No z = new No(t, y.folha);
        rastrear(() -> "divide " + descrever(y) + " -> sobe " + y.chaves[t - 1]);
        z.n = t - 1;
        System.arraycopy(y.chaves, t, z.chaves, 0, t - 1);
        System.arraycopy(y.valores, t, z.valores, 0, t - 1);
        if (!y.folha) {
            System.arraycopy(y.filhos, t, z.filhos, 0, t);
        }
        // abre espaco em x para o novo filho e para a mediana
        System.arraycopy(x.filhos, i + 1, x.filhos, i + 2, x.n - i);
        x.filhos[i + 1] = z;
        System.arraycopy(x.chaves, i, x.chaves, i + 1, x.n - i);
        System.arraycopy(x.valores, i, x.valores, i + 1, x.n - i);
        x.chaves[i] = y.chaves[t - 1];
        x.valores[i] = y.valores[t - 1];
        x.n++;
        y.n = t - 1;
        limparCauda(y);
        modificacoes++;
    }

    // ------------------------------------------------------------ remocao

    /**
     * Remove a chave, se existir. Faz uma unica descida, garantindo antes de
     * descer em um filho que ele tenha pelo menos {@code t} chaves (casos 3a e
     * 3b do Cormen); se a chave estiver em um no interno, e trocada pelo
     * predecessor ou sucessor (2a e 2b) ou os filhos vizinhos sao fundidos (2c).
     *
     * @param chave chave a remover (nao nula)
     * @return valor removido, ou {@code null} se a chave nao existia
     */
    public V remover(K chave) {
        exigirNaoNulo(chave, "chave nula");
        if (tamanho == 0) {
            return null;
        }
        V removido = removerDe(raiz, chave);
        if (raiz.n == 0 && !raiz.folha) {
            raiz = raiz.filhos[0];
            rastrear(() -> "raiz ficou vazia: o filho vira a nova raiz (altura diminui)");
        }
        // mesmo sem achar a chave a descida pode ter reorganizado nos
        modificacoes++;
        if (removido != null) {
            tamanho--;
        }
        return removido;
    }

    private V removerDe(No x, K chave) {
        int i = posicao(x, chave);
        boolean achou = i < x.n && comparar(chave, x.chaves[i]) == 0;

        if (x.folha) {
            if (!achou) {
                return null;
            }
            rastrear(() -> "caso 1: remove " + chave + " da folha " + descrever(x));
            V v = valor(x, i);
            deslocarEsquerda(x, i);
            return v;
        }

        if (achou) {
            V v = valor(x, i);
            No esquerdo = x.filhos[i];
            No direito = x.filhos[i + 1];
            if (esquerdo.n >= t) {
                No p = maisADireita(esquerdo);
                K pred = chave(p, p.n - 1);
                rastrear(() -> "caso 2a: " + chave + " em no interno; troca pelo predecessor " + pred);
                V vPred = removerDe(esquerdo, pred);
                x.chaves[i] = pred;
                x.valores[i] = vPred;
            } else if (direito.n >= t) {
                No s = maisAEsquerda(direito);
                K suc = chave(s, 0);
                rastrear(() -> "caso 2b: " + chave + " em no interno; troca pelo sucessor " + suc);
                V vSuc = removerDe(direito, suc);
                x.chaves[i] = suc;
                x.valores[i] = vSuc;
            } else {
                rastrear(() -> "caso 2c: " + chave + " em no interno; filhos com t-1 chaves sao fundidos");
                fundir(x, i);
                removerDe(esquerdo, chave);
            }
            return v;
        }

        // caso 3: a chave, se existir, esta na subarvore x.filhos[i]
        No filho = x.filhos[i];
        No destino = filho;
        if (filho.n == t - 1) {
            if (i > 0 && x.filhos[i - 1].n >= t) {
                No irmao = x.filhos[i - 1];
                rastrear(() -> "caso 3a: " + descrever(filho) + " empresta do irmao esquerdo " + descrever(irmao));
                emprestarDaEsquerda(x, i);
            } else if (i < x.n && x.filhos[i + 1].n >= t) {
                No irmao = x.filhos[i + 1];
                rastrear(() -> "caso 3a: " + descrever(filho) + " empresta do irmao direito " + descrever(irmao));
                emprestarDaDireita(x, i);
            } else if (i < x.n) {
                No irmao = x.filhos[i + 1];
                Object separadora = x.chaves[i];
                rastrear(() -> "caso 3b: funde " + descrever(filho) + " + " + separadora + " + " + descrever(irmao));
                fundir(x, i);
            } else {
                No irmao = x.filhos[i - 1];
                Object separadora = x.chaves[i - 1];
                rastrear(() -> "caso 3b: funde " + descrever(irmao) + " + " + separadora + " + " + descrever(filho));
                fundir(x, i - 1);
                destino = irmao;
            }
        }
        return removerDe(destino, chave);
    }

    /**
     * Funde {@code x.filhos[i]}, a chave {@code x.chaves[i]} e
     * {@code x.filhos[i + 1]} em um so no (o da esquerda).
     */
    private void fundir(No x, int i) {
        No esq = x.filhos[i];
        No dir = x.filhos[i + 1];
        esq.chaves[esq.n] = x.chaves[i];
        esq.valores[esq.n] = x.valores[i];
        System.arraycopy(dir.chaves, 0, esq.chaves, esq.n + 1, dir.n);
        System.arraycopy(dir.valores, 0, esq.valores, esq.n + 1, dir.n);
        if (!esq.folha) {
            System.arraycopy(dir.filhos, 0, esq.filhos, esq.n + 1, dir.n + 1);
        }
        esq.n += dir.n + 1;
        // retira a chave i e o filho i+1 de x
        System.arraycopy(x.chaves, i + 1, x.chaves, i, x.n - i - 1);
        System.arraycopy(x.valores, i + 1, x.valores, i, x.n - i - 1);
        System.arraycopy(x.filhos, i + 2, x.filhos, i + 1, x.n - i - 1);
        x.n--;
        limparCauda(x);
    }

    /** Rotacao pela direita: a ultima chave do irmao esquerdo sobe, a separadora desce. */
    private static void emprestarDaEsquerda(No x, int i) {
        No filho = x.filhos[i];
        No irmao = x.filhos[i - 1];
        System.arraycopy(filho.chaves, 0, filho.chaves, 1, filho.n);
        System.arraycopy(filho.valores, 0, filho.valores, 1, filho.n);
        filho.chaves[0] = x.chaves[i - 1];
        filho.valores[0] = x.valores[i - 1];
        if (!filho.folha) {
            System.arraycopy(filho.filhos, 0, filho.filhos, 1, filho.n + 1);
            filho.filhos[0] = irmao.filhos[irmao.n];
        }
        filho.n++;
        x.chaves[i - 1] = irmao.chaves[irmao.n - 1];
        x.valores[i - 1] = irmao.valores[irmao.n - 1];
        irmao.n--;
        limparCauda(irmao);
    }

    /** Rotacao pela esquerda: a primeira chave do irmao direito sobe, a separadora desce. */
    private static void emprestarDaDireita(No x, int i) {
        No filho = x.filhos[i];
        No irmao = x.filhos[i + 1];
        filho.chaves[filho.n] = x.chaves[i];
        filho.valores[filho.n] = x.valores[i];
        if (!filho.folha) {
            filho.filhos[filho.n + 1] = irmao.filhos[0];
            System.arraycopy(irmao.filhos, 1, irmao.filhos, 0, irmao.n);
        }
        filho.n++;
        x.chaves[i] = irmao.chaves[0];
        x.valores[i] = irmao.valores[0];
        System.arraycopy(irmao.chaves, 1, irmao.chaves, 0, irmao.n - 1);
        System.arraycopy(irmao.valores, 1, irmao.valores, 0, irmao.n - 1);
        irmao.n--;
        limparCauda(irmao);
    }

    // ------------------------------------------------------------ consultas ordenadas

    /**
     * @return menor chave
     * @throws NoSuchElementException se a arvore estiver vazia
     */
    public K minimo() {
        exigirNaoVazia();
        return chave(maisAEsquerda(raiz), 0);
    }

    /**
     * @return maior chave
     * @throws NoSuchElementException se a arvore estiver vazia
     */
    public K maximo() {
        exigirNaoVazia();
        No x = maisADireita(raiz);
        return chave(x, x.n - 1);
    }

    /**
     * Busca por intervalo fechado {@code [de, ate]}, em ordem crescente. So
     * visita as subarvores que podem conter chaves do intervalo: custa
     * O(t * log_t n + m), sendo {@code m} o numero de chaves devolvidas.
     *
     * @param de limite inferior (inclusivo)
     * @param ate limite superior (inclusivo)
     * @return entradas no intervalo (vazio se {@code de > ate})
     */
    public List<Map.Entry<K, V>> intervalo(K de, K ate) {
        exigirNaoNulo(de, "limite nulo");
        exigirNaoNulo(ate, "limite nulo");
        List<Map.Entry<K, V>> saida = new ArrayList<>();
        if (de.compareTo(ate) <= 0) {
            coletarIntervalo(raiz, de, ate, saida);
        }
        return saida;
    }

    private void coletarIntervalo(No x, K de, K ate, List<Map.Entry<K, V>> saida) {
        for (int i = posicao(x, de); i <= x.n; i++) {
            if (!x.folha) {
                coletarIntervalo(x.filhos[i], de, ate, saida);
            }
            if (i == x.n || comparar(ate, x.chaves[i]) < 0) {
                return;
            }
            saida.add(Map.entry(chave(x, i), valor(x, i)));
        }
    }

    /** @return todas as chaves em ordem crescente (percurso em ordem) */
    public List<K> chaves() {
        List<K> lista = new ArrayList<>(tamanho);
        for (Map.Entry<K, V> e : this) {
            lista.add(e.getKey());
        }
        return lista;
    }

    /**
     * Percurso em ordem (chaves crescentes) com uma pilha explicita, sem
     * recursao. O iterador e fail-fast.
     */
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new Iterator<>() {
            /** Cada quadro e um no e o indice da proxima chave dele a emitir. */
            private final ArrayDeque<int[]> indices = new ArrayDeque<>();
            private final ArrayDeque<No> nos = new ArrayDeque<>();
            private final int modificacoesEsperadas = modificacoes;

            {
                descerPelaEsquerda(raiz);
                descartarEsgotados();
            }

            private void descerPelaEsquerda(No x) {
                while (x != null) {
                    nos.push(x);
                    indices.push(new int[] {0});
                    x = x.folha ? null : x.filhos[0];
                }
            }

            private void descartarEsgotados() {
                while (!nos.isEmpty() && indices.peek()[0] == nos.peek().n) {
                    nos.pop();
                    indices.pop();
                }
            }

            @Override
            public boolean hasNext() {
                return !nos.isEmpty();
            }

            @Override
            public Map.Entry<K, V> next() {
                if (modificacoes != modificacoesEsperadas) {
                    throw new ConcurrentModificationException();
                }
                if (nos.isEmpty()) {
                    throw new NoSuchElementException();
                }
                No x = nos.peek();
                int i = indices.peek()[0]++;
                Map.Entry<K, V> entrada = Map.entry(chave(x, i), valor(x, i));
                if (!x.folha) {
                    descerPelaEsquerda(x.filhos[i + 1]);
                }
                descartarEsgotados();
                return entrada;
            }
        };
    }

    // ------------------------------------------------------------ metricas

    /** @return quantidade de chaves */
    public int tamanho() {
        return tamanho;
    }

    /** @return {@code true} se nao houver chaves */
    public boolean vazia() {
        return tamanho == 0;
    }

    /** @return ordem minima {@code t} */
    public int ordemMinima() {
        return t;
    }

    /** @return numero de arestas da raiz ate as folhas (0 para arvore vazia ou so com a raiz) */
    public int altura() {
        int h = 0;
        for (No x = raiz; !x.folha; x = x.filhos[0]) {
            h++;
        }
        return h;
    }

    /** @return numero de nos da arvore */
    public int contarNos() {
        return contarNos(raiz);
    }

    private static int contarNos(No x) {
        int total = 1;
        if (!x.folha) {
            for (int i = 0; i <= x.n; i++) {
                total += contarNos(x.filhos[i]);
            }
        }
        return total;
    }

    // ------------------------------------------------------------ validacao e impressao

    /**
     * Verifica todos os invariantes da arvore B: limites de chaves por no,
     * chaves ordenadas e dentro do intervalo herdado dos ancestrais, numero de
     * filhos, todas as folhas no mesmo nivel, contagem igual a
     * {@link #tamanho()} e posicoes alem de {@code n} anuladas.
     *
     * @throws IllegalStateException descrevendo o primeiro invariante violado
     */
    public void validar() {
        int[] profundidadeFolhas = {-1};
        int total = validarNo(raiz, null, null, 0, profundidadeFolhas);
        if (total != tamanho) {
            throw new IllegalStateException("tamanho " + tamanho + " mas a arvore tem " + total + " chaves");
        }
        if (raiz.n == 0 && !raiz.folha) {
            throw new IllegalStateException("raiz interna sem chaves");
        }
    }

    private int validarNo(No x, K min, K max, int profundidade, int[] profundidadeFolhas) {
        if (x != raiz && x.n < t - 1) {
            throw new IllegalStateException("no com " + x.n + " chaves (< t-1 = " + (t - 1) + "): " + descrever(x));
        }
        if (x.n > 2 * t - 1) {
            throw new IllegalStateException("no com " + x.n + " chaves (> 2t-1): " + descrever(x));
        }
        for (int i = 0; i < x.n; i++) {
            if (x.chaves[i] == null || x.valores[i] == null) {
                throw new IllegalStateException("chave ou valor nulo na posicao " + i + " de " + descrever(x));
            }
            K k = chave(x, i);
            if (i > 0 && comparar(k, x.chaves[i - 1]) <= 0) {
                throw new IllegalStateException("chaves fora de ordem em " + descrever(x));
            }
            if ((min != null && k.compareTo(min) <= 0) || (max != null && k.compareTo(max) >= 0)) {
                throw new IllegalStateException("chave " + k + " fora do intervalo (" + min + ", " + max + ")");
            }
        }
        for (int i = x.n; i < x.chaves.length; i++) {
            if (x.chaves[i] != null || x.valores[i] != null) {
                throw new IllegalStateException("posicao livre " + i + " nao anulada em " + descrever(x));
            }
        }
        if (x.folha) {
            if (profundidadeFolhas[0] == -1) {
                profundidadeFolhas[0] = profundidade;
            } else if (profundidadeFolhas[0] != profundidade) {
                throw new IllegalStateException("folhas em niveis diferentes: " + profundidadeFolhas[0]
                        + " e " + profundidade);
            }
            return x.n;
        }
        int total = x.n;
        for (int i = 0; i < x.filhos.length; i++) {
            if (i <= x.n && x.filhos[i] == null) {
                throw new IllegalStateException("no interno sem o filho " + i + ": " + descrever(x));
            }
            if (i > x.n && x.filhos[i] != null) {
                throw new IllegalStateException("filho " + i + " nao anulado em " + descrever(x));
            }
        }
        for (int i = 0; i <= x.n; i++) {
            K esq = i == 0 ? min : chave(x, i - 1);
            K dir = i == x.n ? max : chave(x, i);
            total += validarNo(x.filhos[i], esq, dir, profundidade + 1, profundidadeFolhas);
        }
        return total;
    }

    /**
     * Imprime a arvore nivel a nivel (busca em largura), um no entre colchetes.
     * Ex.: {@code nivel 0: [M]} / {@code nivel 1: [D G] [P T X]}.
     *
     * @return representacao em varias linhas
     */
    public String imprimirPorNiveis() {
        StringBuilder sb = new StringBuilder();
        List<No> nivel = List.of(raiz);
        int profundidade = 0;
        while (!nivel.isEmpty()) {
            sb.append("nivel ").append(profundidade++).append(':');
            List<No> proximo = new ArrayList<>();
            for (No x : nivel) {
                sb.append(' ').append(descrever(x));
                if (!x.folha) {
                    for (int i = 0; i <= x.n; i++) {
                        proximo.add(x.filhos[i]);
                    }
                }
            }
            sb.append('\n');
            nivel = proximo;
        }
        return sb.toString();
    }

    /** @return chaves em ordem, ex.: {@code {1=a, 2=b}} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<K, V> e : this) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------ auxiliares

    /** Busca binaria: indice da primeira chave {@code >= chave} no no (ou {@code n}). */
    private int posicao(No x, K chave) {
        int baixo = 0;
        int alto = x.n;
        while (baixo < alto) {
            int meio = (baixo + alto) >>> 1;
            if (comparar(chave, x.chaves[meio]) > 0) {
                baixo = meio + 1;
            } else {
                alto = meio;
            }
        }
        return baixo;
    }

    @SuppressWarnings("unchecked")
    private int comparar(K chave, Object outra) {
        return chave.compareTo((K) outra);
    }

    @SuppressWarnings("unchecked")
    private K chave(No x, int i) {
        return (K) x.chaves[i];
    }

    @SuppressWarnings("unchecked")
    private V valor(No x, int i) {
        return (V) x.valores[i];
    }

    private V substituirValor(No x, int i, V valor) {
        V anterior = valor(x, i);
        x.valores[i] = valor;
        return anterior;
    }

    private static No maisAEsquerda(No x) {
        while (!x.folha) {
            x = x.filhos[0];
        }
        return x;
    }

    private static No maisADireita(No x) {
        while (!x.folha) {
            x = x.filhos[x.n];
        }
        return x;
    }

    /** Abre a posicao {@code i} em uma folha. */
    private static void deslocarDireita(No x, int i) {
        System.arraycopy(x.chaves, i, x.chaves, i + 1, x.n - i);
        System.arraycopy(x.valores, i, x.valores, i + 1, x.n - i);
    }

    /** Fecha a posicao {@code i} em uma folha. */
    private static void deslocarEsquerda(No x, int i) {
        System.arraycopy(x.chaves, i + 1, x.chaves, i, x.n - i - 1);
        System.arraycopy(x.valores, i + 1, x.valores, i, x.n - i - 1);
        x.n--;
        limparCauda(x);
    }

    /** Anula as posicoes alem de {@code n}, para nao reter objetos (e para o validar conferir). */
    private static void limparCauda(No x) {
        for (int i = x.n; i < x.chaves.length && x.chaves[i] != null; i++) {
            x.chaves[i] = null;
            x.valores[i] = null;
        }
        if (!x.folha) {
            for (int i = x.n + 1; i < x.filhos.length && x.filhos[i] != null; i++) {
                x.filhos[i] = null;
            }
        }
    }

    private static String descrever(No x) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < x.n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(x.chaves[i]);
        }
        return sb.append(']').toString();
    }

    /** A mensagem so e montada se houver rastreador, para nao custar nada no uso normal. */
    private void rastrear(Supplier<String> mensagem) {
        if (rastreador != null) {
            rastreador.accept(mensagem.get());
        }
    }

    private void exigirNaoVazia() {
        if (tamanho == 0) {
            throw new NoSuchElementException("arvore vazia");
        }
    }

    private static void exigirNaoNulo(Object o, String mensagem) {
        if (o == null) {
            throw new NullPointerException(mensagem);
        }
    }
}
