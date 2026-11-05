# Árvore B em Java

[![CI](https://github.com/sidsidoso/arvore-b-java/actions/workflows/ci.yml/badge.svg)](https://github.com/sidsidoso/arvore-b-java/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)

Implementação de uma **Árvore B** genérica de **ordem mínima `t`
configurável**, feita do zero em Java 21 para a disciplina de Estrutura de
Dados, seguindo a definição e os algoritmos do Cormen (capítulo 18). Mapeia
chaves `Comparable` para valores e oferece:

- **buscar**, **inserir** (com divisão proativa de nós cheios na descida) e
  **remover** (todos os casos do Cormen: remoção em folha, troca por
  predecessor/sucessor, empréstimo de irmão e fusão);
- **mínimo**, **máximo**, **percurso em ordem** (iterador fail-fast sem
  recursão) e **busca por intervalo** que só visita as subárvores relevantes;
- **altura**, contagem de nós, **impressão por níveis** e um **rastreador**
  opcional que narra cada divisão, empréstimo e fusão;
- **`validar()`**, que confere todos os invariantes e é chamado pelos testes
  depois de cada operação.

Sem dependências externas: compila com `javac` puro e os testes usam um mini
framework próprio.

## Conceito

Uma Árvore B é uma árvore de busca **balanceada e larga**: cada nó guarda
várias chaves ordenadas e tem um filho a mais do que chaves. Com ordem mínima
`t`:

- todo nó tem no máximo **`2t − 1`** chaves e, exceto a raiz, no mínimo
  **`t − 1`**;
- um nó interno com `n` chaves tem **`n + 1`** filhos, e as chaves separam os
  intervalos dos filhos;
- **todas as folhas estão no mesmo nível**: a árvore cresce e encolhe pela
  raiz, nunca pelas folhas.

Exemplo real gerado pela demonstração (`t = 2`, de 1 a 3 chaves por nó), após
inserir `F S Q K C L H T V W M R N P A B X Y D Z E`:

```
 nível 0                          [K Q]
                  ┌─────────────────┼──────────────────┐
 nível 1        [B F]              [M]               [T W]
           ┌──────┼──────┐       ┌──┴──┐         ┌─────┼──────┐
 nível 2  [A]  [C D E]  [H]     [L]  [N P]     [R S]  [V]  [X Y Z]

 Buscar N: em [K Q], K < N < Q -> filho do meio; em [M], N > M -> filho
 da direita; em [N P], achou. Três nós visitados para 21 chaves.
```

Como cada nó tem pelo menos `t` filhos (exceto a raiz), a altura `h` de uma
árvore com `n` chaves satisfaz **`h ≤ log_t((n + 1) / 2)`**. Com `t` grande a
árvore fica muito baixa: um milhão de chaves cabem em altura 2 com `t = 256`.
Por isso Árvores B (e a variante B+) são a estrutura padrão de índices de
bancos de dados e sistemas de arquivos: cada nó cabe em uma página de disco e
cada nível custa um único acesso.

### Inserção com divisão proativa

Na descida, todo nó **cheio** (`2t − 1` chaves) é dividido **antes** de se
descer nele: a mediana sobe para o pai (que, por indução, não está cheio) e o
nó vira dois com `t − 1` chaves. Assim a chave sempre cabe na folha e não é
preciso voltar subindo. Se a raiz estiver cheia, uma nova raiz é criada: é o
único jeito de a altura aumentar.

```
 t = 2, inserir M: a raiz [F Q T] está cheia e é dividida antes de descer

            [F Q T]                              [Q]
       ┌─────┬─┴───┬────┐                   ┌─────┴─────┐
      [C] [H K L] [S] [V W]    ==>         [F]         [T]
                                         ┌──┴──┐      ┌─┴──┐
                                        [C] [H K L]  [S] [V W]

 Depois, ao descer em [F], o filho [H K L] também está cheio e é
 dividido (K sobe), e M entra na folha [L]: [L M].
```

### Remoção (casos do Cormen)

A remoção também é feita em uma única descida, garantindo que **todo nó em que
se desce tenha pelo menos `t` chaves** (uma a mais que o mínimo), de modo que
sempre se pode tirar uma chave dele.

| Caso | Situação                                              | Ação                                                   |
|------|-------------------------------------------------------|--------------------------------------------------------|
| 1    | chave em uma **folha**                                | remove direto                                          |
| 2a   | chave em nó interno; filho esquerdo tem ≥ `t` chaves  | troca pelo **predecessor** e o remove recursivamente   |
| 2b   | chave em nó interno; filho direito tem ≥ `t` chaves   | troca pelo **sucessor** e o remove recursivamente      |
| 2c   | chave em nó interno; os dois filhos têm `t − 1`       | **funde** esquerdo + chave + direito e desce na fusão  |
| 3a   | vai descer em filho com `t − 1`; um irmão tem ≥ `t`    | **empresta** pelo pai (rotação)                        |
| 3b   | vai descer em filho com `t − 1`; irmãos com `t − 1`    | **funde** com um irmão e a chave separadora do pai     |

```
 caso 3a: vai descer em [M] (t-1 chaves) e o irmão esquerdo [B E] tem t

          [K Q]                              [E Q]
      ┌─────┼─────┐                      ┌─────┼──────┐
    [B E]  [M]  [T W]        ==>        [B]  [K M]  [T W]

 E sobe para o pai, K desce para o filho e o último filho de [B E]
 passa a ser o primeiro de [K M] (uma rotação).

 caso 3b: vai descer em [H] (t-1) e o único irmão, [L], também tem t-1

          [K N]                              [N]
      ┌─────┼─────┐                      ┌────┴────┐
     [H]   [L]   [P]         ==>      [H K L]     [P]

 A separadora K desce do pai e se junta aos dois irmãos.
```

Se a raiz interna fica sem chaves após uma fusão, seu único filho vira a nova
raiz: é o único jeito de a altura diminuir.

## Operações e complexidade

`n` = número de chaves, `t` = ordem mínima, `h = O(log_t n)` = altura.

| Operação                   | Descrição                                          | Comparações          | Nós visitados |
|----------------------------|----------------------------------------------------|----------------------|---------------|
| `buscar(k)` / `contem(k)`  | busca a chave                                      | O(log n)             | O(h)          |
| `inserir(k, v)`            | insere ou atualiza; divide nós cheios na descida   | O(log n) + O(t·h) de cópia | O(h)    |
| `remover(k)`               | remove; empresta/funde na descida                  | O(log n) + O(t·h) de cópia | O(h)    |
| `minimo()` / `maximo()`    | desce pela esquerda / direita                      | —                    | O(h)          |
| `intervalo(a, b)`          | entradas com `a ≤ k ≤ b`, em ordem                 | O(log n + m)         | O(h + m/t)    |
| `iterator()` / `chaves()`  | percurso em ordem                                  | —                    | O(n) no total |
| `altura()`                 | número de arestas da raiz às folhas                | —                    | O(h)          |
| `contarNos()`, `validar()`, `imprimirPorNiveis()` | métricas e verificação      | —                    | O(n)          |

`m` é o número de chaves devolvidas pela busca por intervalo. Memória: O(n),
com ocupação média dos nós em torno de 69% (ln 2) para chaves inseridas em
ordem aleatória, como mostra a demonstração.

## Decisões de implementação e otimizações

- **Busca binária dentro do nó**: a posição da chave em cada nó é encontrada
  com busca binária (O(log t)), e não linear (O(t)); com `t` grande isso faz
  diferença e leva o total de comparações para O(log n).
- **Uma única passada** na inserção e na remoção (divisão proativa e garantia
  de `t` chaves na descida), sem pilha de ancestrais nem recursão de volta.
- **Vetores de tamanho fixo por nó** (`2t − 1` chaves e valores, `2t` filhos;
  folhas não alocam vetor de filhos), deslocados com `System.arraycopy`.
- **Atualização sem duplicata**: inserir uma chave existente só troca o valor,
  inclusive no caso sutil em que ela é justamente a mediana que acabou de subir
  numa divisão.
- **Posições livres anuladas** após divisões, fusões e empréstimos, para não
  reter objetos removidos; o `validar()` confere isso também.
- **`validar()` completo**: limites de chaves por nó, chaves estritamente
  crescentes e dentro do intervalo herdado dos ancestrais, número de filhos,
  folhas no mesmo nível, contagem igual a `tamanho()` e posições livres nulas.
- **Rastreador opcional** (`definirRastreador`): recebe uma frase por divisão,
  empréstimo, fusão e caso de remoção. As mensagens são montadas de forma
  preguiçosa (`Supplier`), então não custam nada quando o rastreador está
  desligado.
- **Iterador sem recursão e fail-fast**: usa uma pilha explícita com
  profundidade igual à altura; inserções, remoções e divisões invalidam
  iteradores abertos, mas atualizar o valor de uma chave não.
- **Sem `null`**: chaves e valores nulos são rejeitados, então `null` em
  `buscar`/`remover` significa "não existe".

## Estrutura de pastas

```
arvore-b-java/
├── .github/workflows/ci.yml        # CI: testes + demo no GitHub Actions
├── src/
│   ├── main/java/br/com/sidsidoso/arvoreb/
│   │   ├── ArvoreB.java            # a estrutura de dados
│   │   └── Main.java               # demonstração
│   └── test/java/br/com/sidsidoso/arvoreb/
│       └── Testes.java             # mini framework de testes + casos
├── build.sh                        # build para Linux/macOS
├── build.ps1                       # build para Windows
├── LICENSE
└── README.md
```

## Como compilar, executar e testar

Pré-requisito: **JDK 21** ou superior (`javac` e `java` no `PATH`, ou
`JAVA_HOME` definido para o `build.ps1`).

### Com os scripts

```bash
# Linux / macOS
./build.sh          # só compila para out/
./build.sh run      # compila e executa a demonstração
./build.sh test     # compila e roda os testes
```

```powershell
# Windows (PowerShell)
.\build.ps1
.\build.ps1 run
.\build.ps1 test
```

Os scripts compilam com `-Xlint:all -Werror`, então qualquer aviso do
compilador quebra o build.

### Manualmente

```bash
javac --release 21 -Xlint:all -d out $(find src -name "*.java")
java -cp out br.com.sidsidoso.arvoreb.Main      # demonstração
java -cp out br.com.sidsidoso.arvoreb.Testes    # testes
```

O programa de testes imprime cada caso, termina com `N testes passaram` e sai
com código 1 se algum falhar. Entre os casos estão:

- a árvore exata do exercício 18.2-1 do Cormen e cada caso de remoção
  (1, 2a, 2b, 2c, 3a pelos dois lados, 3b) conferido pelo rastreador e pela
  impressão por níveis;
- o limite teórico de altura, inserções crescentes/decrescentes, remover tudo
  e reutilizar, chaves `String`;
- `validar()` detectando árvores corrompidas de propósito (via reflexão);
- **90 mil operações aleatórias** com `t = 2, 3, 4` e muitas colisões,
  comparadas com `java.util.TreeMap` e com `validar()` **após cada operação**;
- **1,6 milhão de operações aleatórias** (400 mil para cada `t = 2, 5, 16,
  50`), com buscas por intervalo comparadas a `TreeMap.subMap` e `validar()`
  em amostras.

## Exemplo de saída

Saída real de `./build.sh run`:

```
1. Insercao com divisao proativa (t = 2: de 1 a 3 chaves por no)
================================================================
inserindo: F S Q K C L H T V W M R N P A B X Y D Z E
  + F
  + S
  + Q
  + K
     raiz cheia: nova raiz criada (altura aumenta)
     divide [F Q S] -> sobe Q
  + C
  + L
     divide [C F K] -> sobe F
  + H
  + T
  + V
  + W
     divide [S T V] -> sobe T
  + M
     raiz cheia: nova raiz criada (altura aumenta)
     divide [F Q T] -> sobe Q
     divide [H K L] -> sobe K
  + R
  + N
  + P
     divide [L M N] -> sobe M
  + A
     divide [F K M] -> sobe K
  + B
  + X
  + Y
     divide [V W X] -> sobe W
  + D
     divide [A B C] -> sobe B
  + Z
  + E

nivel 0: [K Q]
nivel 1: [B F] [M] [T W]
nivel 2: [A] [C D E] [H] [L] [N P] [R S] [V] [X Y Z]
tamanho=21 altura=2 nos=12
validar(): ok

2. Consultas (valor = ordem de insercao)
========================================
buscar(K)        -> 4
buscar(G)        -> null
minimo()         -> A
maximo()         -> Z
intervalo(H, Q)  -> [H=7, K=4, L=6, M=11, N=13, P=14, Q=3]
percurso em ordem: ABCDEFHKLMNPQRSTVWXYZ

3. Remocao (todos os casos do Cormen)
=====================================
  - F
     caso 2a: F em no interno; troca pelo predecessor E
     caso 1: remove E da folha [C D E]
  - M
     caso 3a: [M] empresta do irmao esquerdo [B E]
     caso 2b: M em no interno; troca pelo sucessor N
     caso 1: remove N da folha [N P]
  - G (nao existe)
     caso 3b: funde [H] + K + [L]
  - D
     caso 3b: funde [B] + E + [N]
     caso 1: remove D da folha [C D]
  - B
     caso 2c: B em no interno; filhos com t-1 chaves sao fundidos
     caso 1: remove B da folha [A B C]
  - X
     caso 1: remove X da folha [X Y Z]
  - S
     caso 1: remove S da folha [R S]
  - N
     caso 2a: N em no interno; troca pelo predecessor L
     caso 1: remove L da folha [H K L]
  - L
     caso 2a: L em no interno; troca pelo predecessor K
     caso 1: remove K da folha [H K]
  - K
     caso 2c: K em no interno; filhos com t-1 chaves sao fundidos
     caso 1: remove K da folha [H K P]
  - P
     caso 3a: [E] empresta do irmao direito [T W]
     caso 1: remove P da folha [H P]
  - Q
     caso 2c: Q em no interno; filhos com t-1 chaves sao fundidos
     caso 1: remove Q da folha [H Q R]
  - T
     caso 2c: T em no interno; filhos com t-1 chaves sao fundidos
     caso 2a: T em no interno; troca pelo predecessor R
     caso 1: remove R da folha [H R]
     raiz ficou vazia: o filho vira a nova raiz (altura diminui)
  - R
     caso 2c: R em no interno; filhos com t-1 chaves sao fundidos
     caso 1: remove R da folha [H R V]
  - V
     caso 1: remove V da folha [H V]

nivel 0: [E W]
nivel 1: [A C] [H] [Y Z]
tamanho=7 altura=1; validar() apos cada remocao: ok

4. Escala: 1000000 chaves aleatorias distintas
==============================================
    t  altura   altura maxima        nos   ocupacao  apos remover metade
    2      15              18     570004      58.5%  tamanho=500000 altura=11 nos=340747
    4       8               9     216699      65.9%  tamanho=500000 altura=7 nos=129859
   16       4               4      46916      68.8%  tamanho=500000 altura=4 nos=26427
   64       3               3      11354      69.4%  tamanho=500000 altura=2 nos=6219
  256       2               2       2704      72.4%  tamanho=500000 altura=2 nos=1484
altura maxima = floor(log_t((n+1)/2)); ocupacao = chaves / (nos * (2t-1))
```

## Referências

- CORMEN, T. H. et al. *Algoritmos: teoria e prática*. 3. ed. Elsevier, 2012.
  Capítulo 18 (Árvores B), incluindo o exercício 18.2-1.
- BAYER, R.; McCREIGHT, E. *Organization and Maintenance of Large Ordered
  Indexes*. Acta Informatica, v. 1, p. 173–189, 1972.
- COMER, D. *The Ubiquitous B-Tree*. ACM Computing Surveys, v. 11, n. 2,
  p. 121–137, 1979.
- YAO, A. C. *On Random 2–3 Trees*. Acta Informatica, v. 9, p. 159–170, 1978
  (ocupação esperada de ~69% sob inserções aleatórias).
- [Documentação de `java.util.TreeMap`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html)
