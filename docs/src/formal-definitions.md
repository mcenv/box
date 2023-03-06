# Formal Definitions

$$
\newcommand{\Tag}[0]{\texttt{Tag}}
\newcommand{\EndTag}[0]{\texttt{EndTag}}
\newcommand{\ByteTag}[0]{\texttt{ByteTag}}
\newcommand{\ShortTag}[0]{\texttt{ShortTag}}
\newcommand{\IntTag}[0]{\texttt{IntTag}}
\newcommand{\LongTag}[0]{\texttt{LongTag}}
\newcommand{\FloatTag}[0]{\texttt{FloatTag}}
\newcommand{\DoubleTag}[0]{\texttt{DoubleTag}}
\newcommand{\StringTag}[0]{\texttt{StringTag}}
\newcommand{\ByteArrayTag}[0]{\texttt{ByteArrayTag}}
\newcommand{\IntArrayTag}[0]{\texttt{IntArrayTag}}
\newcommand{\LongArrayTag}[0]{\texttt{LongArrayTag}}
\newcommand{\ListTag}[0]{\texttt{ListTag}}
\newcommand{\CompoundTag}[0]{\texttt{CompoundTag}}
\newcommand{\Type}[1]{\texttt{Type} \ #1}
\newcommand{\Bool}[0]{\texttt{Bool}}
\newcommand{\false}[0]{\texttt{false}}
\newcommand{\true}[0]{\texttt{true}}
\newcommand{\if}[3]{\texttt{if} \ #1 \ \texttt{then} \ #2 \ \texttt{else} \ #3}
\newcommand{\is}[2]{#1 \ \texttt{is} \ #2}
\newcommand{\Byte}[0]{\texttt{Byte}}
\newcommand{\byte}[1]{#1 \texttt{b}}
\newcommand{\Short}[0]{\texttt{Short}}
\newcommand{\short}[1]{#1 \texttt{s}}
\newcommand{\Int}[0]{\texttt{Int}}
\newcommand{\Long}[0]{\texttt{Long}}
\newcommand{\long}[1]{#1 \texttt{L}}
\newcommand{\Float}[0]{\texttt{Float}}
\newcommand{\float}[1]{#1 \texttt{f}}
\newcommand{\Double}[0]{\texttt{Double}}
\newcommand{\double}[1]{#1 \texttt{d}}
\newcommand{\String}[0]{\texttt{String}}
\newcommand{\string}[1]{\texttt{"} #1 \texttt{"}}
\newcommand{\ByteArray}[0]{\texttt{ByteArray}}
\newcommand{\bytearray}[1]{\texttt{[Byte;} #1 \texttt{]}}
\newcommand{\IntArray}[0]{\texttt{IntArray}}
\newcommand{\intarray}[1]{\texttt{[Int;} #1 \texttt{]}}
\newcommand{\LongArray}[0]{\texttt{LongArray}}
\newcommand{\longarray}[1]{\texttt{[Long;} #1 \texttt{]}}
\newcommand{\List}[1]{\texttt{List} \ #1}
\newcommand{\list}[1]{\texttt{[} #1 \texttt{]}}
\newcommand{\Compound}[1]{\texttt{Compound\\{} \ #1 \ \texttt{\\}}}
\newcommand{\compound}[1]{\texttt{\\{} \ #1 \texttt{\\}}}
\newcommand{\Union}[1]{\texttt{Union\\{} #1 \texttt{\\}}}
\newcommand{\Func}[3]{\texttt{Func(} #1 : #2 \texttt{)} \rightarrow #3}
\newcommand{\func}[2]{\backslash #1 \rightarrow #2}
\newcommand{\apply}[2]{#1 \ @ \ #2}
\newcommand{\Code}[1]{\texttt{Code} \ #1}
\newcommand{\code}[1]{\texttt{`} #1}
\newcommand{\splice}[1]{\texttt{\\$} #1}
\newcommand{\let}[3]{\texttt{let} \ #1 \ \texttt{=} \ #2 \texttt{;} \ #3}
\newcommand{\intrange}[2]{#1 \texttt{..} #2}
\newcommand{\drop}[0]{\texttt{_}}
\newcommand{\synth}[0]{\color{red} \blacktriangle}
\newcommand{\check}[0]{\color{blue} \blacktriangledown}
\newcommand{\rels}[3]{#1 \vdash #2 \ \synth \ #3}
\newcommand{\relc}[3]{#1 \vdash #2 \ \check \ #3}
\newcommand{\relsp}[4]{#1 \vdash #2 \ \synth \ #3 \dashv #4}
\newcommand{\relcp}[4]{#1 \vdash #2 \ \check \ #3 \dashv #4}
\newcommand{\relsub}[3]{#1 \vdash #2 <: #3}
\newcommand{\relf}[2]{\vdash \texttt{fresh} \ #1 : #2}
\newcommand{\infer}[2]{\dfrac{#1}{#2}}
$$

## Syntax

## Typing Rules

- \\(\boxed{\rels{\Gamma}{t}{A}}\\): Under context \\(\Gamma\\), term \\(t\\) synthesizes type \\(A\\)
- \\(\boxed{\relc{\Gamma}{t}{A}}\\): Under context \\(\Gamma\\), term \\(t\\) checks against type \\(A\\)
- \\(\boxed{\relsp{\Gamma}{p}{A}{\Delta}}\\): Under context \\(\Gamma\\), pattern \\(p\\) synthesizes type \\(A\\) and outputs bindings \\(\Delta\\)
- \\(\boxed{\relcp{\Gamma}{p}{A}{\Delta}}\\): Under context \\(\Gamma\\), pattern \\(p\\) checks against type \\(A\\) and outputs bindings \\(\Delta\\)
- \\(\boxed{\relsub{\Gamma}{A}{B}}\\): Under context \\(\Gamma\\), type \\(A\\) is a subtype of type \\(B\\)
- \\(\boxed{\relf{t}{A}}\\): Term \\(t\\) is fresh and has type \\(A\\)

### Terms

#### Tag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Tag }{ \Type{\ByteTag} }
}
\\]

#### EndTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \EndTag }{ \Tag }
}
\\]

#### ByteTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \ByteTag }{ \Tag }
}
\\]

#### ShortTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \ShortTag }{ \Tag }
}
\\]

#### IntTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \IntTag }{ \Tag }
}
\\]

#### LongTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \LongTag }{ \Tag }
}
\\]

#### FloatTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \FloatTag }{ \Tag }
}
\\]

#### DoubleTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \DoubleTag }{ \Tag }
}
\\]

#### StringTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \StringTag }{ \Tag }
}
\\]

#### ByteArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \ByteArrayTag }{ \Tag }
}
\\]

#### IntArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \IntArrayTag }{ \Tag }
}
\\]

#### LongArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \LongArrayTag }{ \Tag }
}
\\]

#### ListTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \ListTag }{ \Tag }
}
\\]

#### CompoundTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \CompoundTag }{ \Tag }
}
\\]

#### Type-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ \tau }{ \Tag }
}{
\rels{ \Gamma }{ \Type{\tau} }{ \Type{\ByteTag} }
}
\\]

#### Bool-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Bool }{ \Type{\ByteTag} }
}
\\]

#### BoolOf-\\(\synth\\)

\\[
\infer{
n \in \\{ \false, \true \\}
}{
\rels{ \Gamma }{ n }{ \Bool }
}
\\]

#### If-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ t_1 }{ \Bool } \\\\
\rels{ \Gamma }{ t_2 }{ A_2 } \\\\
\rels{ \Gamma }{ t_3 }{ A_3 }
}{
\rels{ \Gamma }{ \if{t_1}{t_2}{t_3} }{ \Union{A_2, A_3} }
}
\\]

#### If-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ t_1 }{ \Bool } \\\\
\relc{ \Gamma }{ t_2 }{ A } \\\\
\relc{ \Gamma }{ t_3 }{ A }
}{
\relc{ \Gamma }{ \if{t_1}{t_2}{t_3} }{ A }
}
\\]

#### Is-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ p }{ A }{ \Delta } \\\\
\relc{ \Gamma }{ t }{ A }
}{
\rels{ \Gamma }{ \is{t}{p} }{ \Bool }
}
\\]

#### Byte-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Byte }{ \Type{\ByteTag} }
}
\\]

#### ByteOf-\\(\synth\\)

\\[
\infer{
\texttt{byte} \ n
}{
\rels{ \Gamma }{ \byte{n} }{ \Byte }
}
\\]

#### Short-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Short }{ \Type{\ShortTag} }
}
\\]

#### ShortOf-\\(\synth\\)

\\[
\infer{
\texttt{short} \ n
}{
\rels{ \Gamma }{ \short{n} }{ \Short }
}
\\]

#### Int-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Int }{ \Type{\IntTag} }
}
\\]

#### IntOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n
}{
\rels{ \Gamma }{ n }{ \Int }
}
\\]

#### Long-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Long }{ \Type{\LongTag} }
}
\\]

#### LongOf-\\(\synth\\)

\\[
\infer{
\texttt{long} \ n
}{
\rels{ \Gamma }{ \long{n} }{ \Long }
}
\\]

#### Float-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Float }{ \Type{\FloatTag} }
}
\\]

#### FloatOf-\\(\synth\\)

\\[
\infer{
\texttt{float} \ n
}{
\rels{ \Gamma }{ \float{n} }{ \Float }
}
\\]

#### Double-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \Double }{ \Type{\DoubleTag} }
}
\\]

#### DoubleOf-\\(\synth\\)

\\[
\infer{
\texttt{double} \ n
}{
\rels{ \Gamma }{ \double{n} }{ \Double }
}
\\]

#### String-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \String }{ \Type{\StringTag} }
}
\\]

#### StringOf-\\(\synth\\)

\\[
\infer{
\texttt{java.lang.String} \ n
}{
\rels{ \Gamma }{ \string{n} }{ \String }
}
\\]

#### ByteArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \ByteArray }{ \Type{\ByteArrayTag} }
}
\\]

#### ByteArrayOf-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ t_i }{ \Byte }
}{
\rels{ \Gamma }{ \bytearray{t_1, t_2, \dots, t_n} }{ \ByteArray }
}
\\]

#### IntArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \IntArray }{ \Type{\IntArrayTag} }
}
\\]

#### IntArrayOf-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ t_i }{ \Int }
}{
\rels{ \Gamma }{ \intarray{t_1, t_2, \dots, t_n} }{ \IntArray }
}
\\]

#### LongArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ \LongArray }{ \Type{\LongArrayTag} }
}
\\]

#### LongArrayOf-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ t_i }{ \Long }
}{
\rels{ \Gamma }{ \longarray{t_1, t_2, \dots, t_n} }{ \LongArray }
}
\\]

#### List-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma }{ A }{ \Type{\tau} }
}{
\rels{ \Gamma }{ \List{A} }{ \Type{\ByteTag} }
}
\\]

#### ListOf-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ t_i }{ A_i }
}{
\rels{ \Gamma }{ \list{t_1, t_2, \dots, t_n} }{ \List{\Union{A_1, A_2, \dots, A_n}} }
}
\\]

#### ListOf-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ t_i }{ A }
}{
\relc{ \Gamma }{ \list{t_1, t_2, \dots, t_n} }{ \List{A} }
}
\\]

#### Compound-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ A_i }{ \Type{\tau} }
}{
\rels{ \Gamma }{ \Compound{k_1: A_1, k_2: A_2, \dots, k_n: A_n} }{ \Type{\ByteTag} }
}
\\]

#### CompoundOf-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ t_i }{ A_i }
}{
\rels{ \Gamma }{ \compound{k_1: t_1, k_2: t_2, \dots, k_n: t_n} }{ \Compound{k_1: A_1, k_2: A_2, \dots, k_n: A_n} }
}
\\]

#### CompoundOf-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ t_i }{ A }
}{
\relc{ \Gamma }{ \compound{k_1: t_1, k_2: t_2, \dots, k_n: t_n} }{ \Compound{k_1: A_1, k_2: A_2, \dots, k_n: A_n} }
}
\\]

#### Union-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma }{ A_i }{ \Type{\tau} }
}{
\rels{ \Gamma }{ \Union{A_1, A_2, \dots, A_n} }{ \Type{\tau} }
}
\\]

#### Union-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ A_i }{ \Type{\tau} }
}{
\relc{ \Gamma }{ \Union{A_1, A_2, \dots, A_n} }{ \Type{\tau} }
}
\\]

#### Func-\\(\synth\\)

\\[
\infer{
\relf{ \tau_1 }{ \Tag } \\\\
\relf{ \tau_2 }{ \Tag } \\\\
\relc{ \Gamma }{ A_1 }{ \Type{\tau_1} } \\\\
\relcp{ \Gamma }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma, \Delta }{ A_2 }{ \Type{\tau_2} }
}{
\rels{ \Gamma }{ \Func{p}{A_1}{A_2} }{ \Type{\CompoundTag} }
}
\\]

#### FuncOf-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ p }{ A_1 }{ \Delta } \\\\
\rels{ \Gamma, \Delta }{ t }{ A_2 }
}{
\rels{ \Gamma }{ \func{p}{t} }{ \Func{p_1}{A_1}{A_2} }
}
\\]

#### FuncOf-\\(\check\\)

\\[
\infer{
\relcp{ \Gamma }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma, \Delta }{ t }{ A_2 }
}{
\relc{ \Gamma }{ \func{p}{t} }{ \Func{p_1}{A_1}{A_2} }
}
\\]

#### Apply-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ t_1 }{ \Func{p}{A_1}{A_2} } \\\\
\relc{ \Gamma }{ t_2 }{ A_1}
}{
\rels{ \Gamma }{ \apply{t_1}{t_2} }{ A_2 }
}
\\]

#### Apply-\\(\check\\)

\\[
\infer{
\rels{ \Gamma }{ t_2 }{ A_1 } \\\\
\relc{ \Gamma }{ t_1 }{ \Func{p}{A_1}{A_2} }
}{
\relc{ \Gamma }{ \apply{t_1}{t_2} }{ A_2 }
}
\\]

#### Code-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma }{ A }{ \Type{\tau} }
}{
\rels{ \Gamma }{ \Code{A} }{ \Type{\ByteTag} }
}
\\]

#### CodeOf-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ t }{ A }
}{
\rels{ \Gamma }{ \code{t} }{ \Code{A} }
}
\\]

#### CodeOf-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ t }{ A }
}{
\relc{ \Gamma }{ \code{t} }{ \Code{A} }
}
\\]

#### Splice-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ t }{ \Code{A} }
}{
\rels{ \Gamma }{ \splice{t} }{ A }
}
\\]

#### Splice-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ t }{ \Code{A} }
}{
\relc{ \Gamma }{ \splice{t} }{ A }
}
\\]

#### Let-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma }{ t_1 }{ A_1 } \\\\
\rels{ \Gamma, \Delta }{ t_2 }{ A_2 }
}{
\rels{ \Gamma }{ \let{p}{t_1}{t_2} }{ A_2 }
}
\\]

#### Let-\\(\check\\)

\\[
\infer{
\relsp{ \Gamma }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma }{ t_1 }{ A_1 } \\\\
\relc{ \Gamma, \Delta }{ t_2 }{ A_2 }
}{
\relc{ \Gamma }{ \let{p}{t_1}{t_2} }{ A_2 }
}
\\]

#### Var-\\(\synth\\)

\\[
\infer{
(x : A) \in \Gamma
}{
\rels{ \Gamma }{ x }{ A }
}
\\]

#### Sub-\\(\check\\)

\\[
\infer{
\rels{ \Gamma }{ t }{ A } \\\\
\relsub{ \Gamma }{ A }{ B }
}{
\relc{ \Gamma }{ t }{ B }
}
\\]

### Patterns

#### IntOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n
}{
\relsp{ \Gamma }{ n }{ \Int }{ \Delta } \\\\
}
\\]

#### IntRangeOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n_1 \\\\
\texttt{int} \ n_2 \\\\
n_1 \ \texttt{<=} \ n_2
}{
\relsp{ \Gamma }{ \intrange{n_1}{n_2} }{ \Int }{ \Delta } \\\\
}
\\]

#### CompoundOf-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ p_i }{ A_i }{ \Delta_i }
}{
\relsp{ \Gamma }{ \compound{k_1: p_1, k_2: p_2, \dots, k_n: p_n} }{ \Compound{k_1: A_1, k_2: A_2, \dots, k_n: A_n} }{ \Delta_1, \Delta_2, \dots, \Delta_n } \\\\
}
\\]

#### CompoundOf-\\(\check\\)

\\[
\infer{
\relcp{ \Gamma }{ p_i }{ A_i }{ \Delta_i }
}{
\relcp{ \Gamma }{ \compound{k_1: p_1, k_2: p_2, \dots, k_n: p_n} }{ \Compound{k_1: A_1, k_2: A_2, \dots, k_n: A_n} }{ \Delta_1, \Delta_2, \dots, \Delta_n } \\\\
}
\\]

#### TupleOf-\\(\synth\\)

#### TupleOf-\\(\check\\)

#### Var-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relf{ A }{ \Type{\tau} }
}{
\relsp{ \Gamma }{ x }{ A }{ x : A } \\\\
}
\\]

#### Var-\\(\check\\)

\\[
\infer{}{
\relcp{ \Gamma }{ x }{ A }{ x : A } \\\\
}
\\]

#### Drop-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relf{ A }{ \Type{\tau} }
}{
\relsp{ \Gamma }{ \drop }{ A }{ \cdot } \\\\
}
\\]

#### Drop-\\(\check\\)

\\[
\infer{}{
\relcp{ \Gamma }{ \drop }{ A }{ \cdot } \\\\
}
\\]

#### Sub-\\(\check\\)

\\[
\infer{
\relsp{ \Gamma }{ p }{ A }{ \Delta } \\\\
\relsub{ \Gamma }{ A }{ B }
}{
\relcp{ \Gamma }{ p }{ B }{ \Delta } \\\\
}
\\]
