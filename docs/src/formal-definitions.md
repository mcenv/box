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
\newcommand{\Func}[2]{\texttt{Func(} #1 \texttt{)} \rightarrow #2}
\newcommand{\func}[2]{\texttt{\\\\(} #1 \texttt{)} \rightarrow #2}
\newcommand{\apply}[2]{#1 \texttt{(} #2 \texttt{)}}
\newcommand{\Code}[1]{\texttt{Code} \ #1}
\newcommand{\code}[1]{\texttt{`} #1}
\newcommand{\splice}[1]{\texttt{\\$} #1}
\newcommand{\let}[3]{\texttt{let} \ #1 \ \texttt{=} \ #2 \texttt{;} \ #3}
\newcommand{\intrange}[2]{#1 \texttt{..} #2}
\newcommand{\drop}[0]{\texttt{_}}
\newcommand{\synth}[0]{\color{red} \blacktriangle}
\newcommand{\check}[0]{\color{blue} \blacktriangledown}
\newcommand{\rels}[4]{#1 \vdash _{#2} #3 \ \synth \ #4}
\newcommand{\relc}[4]{#1 \vdash _{#2} #3 \ \check \ #4}
\newcommand{\relsp}[5]{#1 \vdash _{#2} #3 \ \synth \ #4 \dashv #5}
\newcommand{\relcp}[5]{#1 \vdash _{#2} #3 \ \check \ #4 \dashv #5}
\newcommand{\relsub}[3]{#1 \vdash #2 <: #3}
\newcommand{\relf}[2]{\vdash \texttt{fresh} \ #1 : #2}
\newcommand{\infer}[2]{\dfrac{#1}{#2}}
$$

## Syntax

## Typing Rules

- \\(\boxed{\rels{ \Gamma }{ s }{ t }{ A }}\\): Under context \\(\Gamma\\), at stage \\(s\\), term \\(t\\) synthesizes type \\(A\\)
- \\(\boxed{\relc{ \Gamma }{ s }{ t }{ A }}\\): Under context \\(\Gamma\\), at stage \\(s\\), term \\(t\\) checks against type \\(A\\)
- \\(\boxed{\relsp{ \Gamma }{ s }{ p }{ A }{ \Delta }}\\): Under context \\(\Gamma\\), at stage \\(s\\), pattern \\(p\\) synthesizes type \\(A\\) and outputs bindings \\(\Delta\\)
- \\(\boxed{\relcp{ \Gamma }{ s }{ p }{ A }{ \Delta }}\\): Under context \\(\Gamma\\), at stage \\(s\\), pattern \\(p\\) checks against type \\(A\\) and outputs bindings \\(\Delta\\)
- \\(\boxed{\relsub{ \Gamma }{ A }{ B }}\\): Under context \\(\Gamma\\), type \\(A\\) is a subtype of type \\(B\\)
- \\(\boxed{\relf{ t }{ A }}\\): Term \\(t\\) is fresh and has type \\(A\\)

### Terms

#### Tag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s+1 }{ \Tag }{ \Type{\ByteTag} }
}
\\]

#### EndTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \EndTag }{ \Tag }
}
\\]

#### ByteTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \ByteTag }{ \Tag }
}
\\]

#### ShortTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \ShortTag }{ \Tag }
}
\\]

#### IntTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \IntTag }{ \Tag }
}
\\]

#### LongTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \LongTag }{ \Tag }
}
\\]

#### FloatTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \FloatTag }{ \Tag }
}
\\]

#### DoubleTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \DoubleTag }{ \Tag }
}
\\]

#### StringTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \StringTag }{ \Tag }
}
\\]

#### ByteArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \ByteArrayTag }{ \Tag }
}
\\]

#### IntArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \IntArrayTag }{ \Tag }
}
\\]

#### LongArrayTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \LongArrayTag }{ \Tag }
}
\\]

#### ListTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \ListTag }{ \Tag }
}
\\]

#### CompoundTag-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \CompoundTag }{ \Tag }
}
\\]

#### SubTag-\\(\check\\)

\\[
\infer{
\rels{ \Gamma }{ s }{ t }{ \Tag }
}{
\relc{ \Gamma }{ s+1 }{ t }{ \Tag }
}
\\]

> TODO: Use stage coercion instead?

#### Type-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ s }{ \tau }{ \Tag }
}{
\rels{ \Gamma }{ s }{ \Type{\tau} }{ \Type{\ByteTag} }
}
\\]

#### Bool-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Bool }{ \Type{\ByteTag} }
}
\\]

#### BoolOf-\\(\synth\\)

\\[
\infer{
n \in \\{ \false, \true \\}
}{
\rels{ \Gamma }{ s }{ n }{ \Bool }
}
\\]

#### If-\\(\synth\\)

\\[
\infer{
\relc{ \Gamma }{ s }{ t_1 }{ \Bool } \\\\
\rels{ \Gamma }{ s }{ t_2 }{ A_2 } \\\\
\rels{ \Gamma }{ s }{ t_3 }{ A_3 }
}{
\rels{ \Gamma }{ s }{ \if{t_1}{t_2}{t_3} }{ \Union{A_2, A_3} }
}
\\]

#### If-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ s }{ t_1 }{ \Bool } \\\\
\relc{ \Gamma }{ s }{ t_2 }{ A } \\\\
\relc{ \Gamma }{ s }{ t_3 }{ A }
}{
\relc{ \Gamma }{ s }{ \if{t_1}{t_2}{t_3} }{ A }
}
\\]

#### Is-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ s }{ p }{ A }{ \Delta } \\\\
\relc{ \Gamma }{ s }{ t }{ A }
}{
\rels{ \Gamma }{ s }{ \is{t}{p} }{ \Bool }
}
\\]

#### Byte-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Byte }{ \Type{\ByteTag} }
}
\\]

#### ByteOf-\\(\synth\\)

\\[
\infer{
\texttt{byte} \ n
}{
\rels{ \Gamma }{ s }{ \byte{n} }{ \Byte }
}
\\]

#### Short-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Short }{ \Type{\ShortTag} }
}
\\]

#### ShortOf-\\(\synth\\)

\\[
\infer{
\texttt{short} \ n
}{
\rels{ \Gamma }{ s }{ \short{n} }{ \Short }
}
\\]

#### Int-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Int }{ \Type{\IntTag} }
}
\\]

#### IntOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n
}{
\rels{ \Gamma }{ s }{ n }{ \Int }
}
\\]

#### Long-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Long }{ \Type{\LongTag} }
}
\\]

#### LongOf-\\(\synth\\)

\\[
\infer{
\texttt{long} \ n
}{
\rels{ \Gamma }{ s }{ \long{n} }{ \Long }
}
\\]

#### Float-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Float }{ \Type{\FloatTag} }
}
\\]

#### FloatOf-\\(\synth\\)

\\[
\infer{
\texttt{float} \ n
}{
\rels{ \Gamma }{ s }{ \float{n} }{ \Float }
}
\\]

#### Double-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \Double }{ \Type{\DoubleTag} }
}
\\]

#### DoubleOf-\\(\synth\\)

\\[
\infer{
\texttt{double} \ n
}{
\rels{ \Gamma }{ s }{ \double{n} }{ \Double }
}
\\]

#### String-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \String }{ \Type{\StringTag} }
}
\\]

#### StringOf-\\(\synth\\)

\\[
\infer{
\texttt{java.lang.String} \ n
}{
\rels{ \Gamma }{ s }{ \string{n} }{ \String }
}
\\]

#### ByteArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \ByteArray }{ \Type{\ByteArrayTag} }
}
\\]

#### ByteArrayOf-\\(\synth\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ t_n }{ \Byte }
}{
\rels{ \Gamma }{ s }{ \bytearray{\dots, t_n} }{ \ByteArray }
}
\\]

#### IntArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \IntArray }{ \Type{\IntArrayTag} }
}
\\]

#### IntArrayOf-\\(\synth\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ t_n }{ \Int }
}{
\rels{ \Gamma }{ s }{ \intarray{\dots, t_n} }{ \IntArray }
}
\\]

#### LongArray-\\(\synth\\)

\\[
\infer{}{
\rels{ \Gamma }{ s }{ \LongArray }{ \Type{\LongArrayTag} }
}
\\]

#### LongArrayOf-\\(\synth\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ t_n }{ \Long }
}{
\rels{ \Gamma }{ s }{ \longarray{\dots, t_n} }{ \LongArray }
}
\\]

#### List-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma }{ s }{ A }{ \Type{\tau} }
}{
\rels{ \Gamma }{ s }{ \List{A} }{ \Type{\ByteTag} }
}
\\]

#### ListOf-\\(\synth\\)

\\[
\infer{
\dots \rels{ \Gamma }{ s }{ t_n }{ A_n }
}{
\rels{ \Gamma }{ s }{ \list{\dots, t_n} }{ \List{\Union{\dots, A_n}} }
}
\\]

#### ListOf-\\(\check\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ t_n }{ A }
}{
\relc{ \Gamma }{ s }{ \list{\dots, t_n} }{ \List{A} }
}
\\]

#### Compound-\\(\synth\\)

\\[
\infer{
\dots \begin{cases}
\relf{ \tau_n }{ \Tag } \\\\
\relc{ \Gamma }{ s }{ A_n }{ \Type{\tau_n} }
\end{cases}
}{
\rels{ \Gamma }{ s }{ \Compound{\dots, k_n: A_n} }{ \Type{\ByteTag} }
}
\\]

#### CompoundOf-\\(\synth\\)

\\[
\infer{
\dots \rels{ \Gamma }{ s }{ t_n }{ A_n }
}{
\rels{ \Gamma }{ s }{ \compound{\dots, k_n: t_n} }{ \Compound{\dots, k_n: A_n} }
}
\\]

#### CompoundOf-\\(\check\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ t_n }{ A }
}{
\relc{ \Gamma }{ s }{ \compound{\dots, k_n: t_n} }{ \Compound{\dots, k_n: A_n} }
}
\\]

#### Union-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\dots \relc{ \Gamma }{ s }{ A_n }{ \Type{\tau} }
}{
\rels{ \Gamma }{ s }{ \Union{\dots, A_n} }{ \Type{\tau} }
}
\\]

#### Union-\\(\check\\)

\\[
\infer{
\dots \relc{ \Gamma }{ s }{ A_n }{ \Type{\tau} }
}{
\relc{ \Gamma }{ s }{ \Union{\dots, A_n} }{ \Type{\tau} }
}
\\]

#### Func-\\(\synth\\)

\\[
\infer{
\dots \begin{cases}
\relf{ \tau_n }{ \Tag } \\\\
\relc{ \Gamma, \dots, \Delta_{n-1} }{ s }{ A_n }{ \Type{\tau_n} } \\\\
\relcp{ \Gamma, \dots, \Delta_{n-1} }{ s }{ p_n }{ A_n }{ \Delta_n }
\end{cases} \\\\
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma, \dots, \Delta_n }{ s }{ B }{ \Type{\tau} }
}{
\rels{ \Gamma }{ s }{ \Func{\dots, p_n: A_n}{B} }{ \Type{\CompoundTag} }
}
\\]

#### FuncOf-\\(\synth\\)

\\[
\infer{
\dots \relsp{ \Gamma }{ s }{ p_n }{ A_n }{ \Delta_n } \\\\
\rels{ \Gamma, \dots, \Delta_n }{ s }{ t }{ B }
}{
\rels{ \Gamma }{ s }{ \func{\dots, p_n}{t} }{ \Func{\dots, p_n: A_n}{B} }
}
\\]

#### FuncOf-\\(\check\\)

\\[
\infer{
\dots \relcp{ \Gamma }{ s }{ p_n }{ A_n }{ \Delta_n } \\\\
\relc{ \Gamma, \dots, \Delta_n }{ s }{ t }{ B }
}{
\relc{ \Gamma }{ s }{ \func{\dots, p_n}{t} }{ \Func{\dots, p_n: A_n}{B} }
}
\\]

#### Apply-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ s }{ t }{ \Func{\dots, p_n: A_n}{B} } \\\\
\dots \relc{ \Gamma }{ s }{ t_n }{ A_n }
}{
\rels{ \Gamma }{ s }{ \apply{t}{\dots, t_n} }{ B }
}
\\]

#### Apply-\\(\check\\)

\\[
\infer{
\dots \rels{ \Gamma }{ s }{ t_n }{ A_n } \\\\
\relc{ \Gamma }{ s }{ t }{ \Func{\dots, \drop: A_n}{B} }
}{
\relc{ \Gamma }{ s }{ \apply{t}{\dots, t_n} }{ B }
}
\\]

#### Code-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relc{ \Gamma }{ s }{ A }{ \Type{\tau} }
}{
\rels{ \Gamma }{ s+1 }{ \Code{A} }{ \Type{\ByteTag} }
}
\\]

#### CodeOf-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ s }{ t }{ A }
}{
\rels{ \Gamma }{ s+1 }{ \code{t} }{ \Code{A} }
}
\\]

#### CodeOf-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ s }{ t }{ A }
}{
\relc{ \Gamma }{ s+1 }{ \code{t} }{ \Code{A} }
}
\\]

#### Splice-\\(\synth\\)

\\[
\infer{
\rels{ \Gamma }{ s+1 }{ t }{ \Code{A} }
}{
\rels{ \Gamma }{ s }{ \splice{t} }{ A }
}
\\]

#### Splice-\\(\check\\)

\\[
\infer{
\relc{ \Gamma }{ s+1 }{ t }{ \Code{A} }
}{
\relc{ \Gamma }{ s }{ \splice{t} }{ A }
}
\\]

#### Let-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ s }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma }{ s }{ t_1 }{ A_1 } \\\\
\rels{ \Gamma, \Delta }{ s }{ t_2 }{ A_2 }
}{
\rels{ \Gamma }{ s }{ \let{p}{t_1}{t_2} }{ A_2 }
}
\\]

#### Let-\\(\check\\)

\\[
\infer{
\relsp{ \Gamma }{ s }{ p }{ A_1 }{ \Delta } \\\\
\relc{ \Gamma }{ s }{ t_1 }{ A_1 } \\\\
\relc{ \Gamma, \Delta }{ s }{ t_2 }{ A_2 }
}{
\relc{ \Gamma }{ s }{ \let{p}{t_1}{t_2} }{ A_2 }
}
\\]

#### Var-\\(\synth\\)

\\[
\infer{
(x :_s A) \in \Gamma
}{
\rels{ \Gamma }{ s }{ x }{ A }
}
\\]

#### Sub-\\(\check\\)

\\[
\infer{
\rels{ \Gamma }{ s }{ t }{ A } \\\\
\relsub{ \Gamma }{ A }{ B }
}{
\relc{ \Gamma }{ s }{ t }{ B }
}
\\]

### Patterns

#### IntOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n
}{
\relsp{ \Gamma }{ s }{ n }{ \Int }{ \Delta }
}
\\]

#### IntRangeOf-\\(\synth\\)

\\[
\infer{
\texttt{int} \ n_1 \\\\
\texttt{int} \ n_2 \\\\
n_1 \ \texttt{<=} \ n_2
}{
\relsp{ \Gamma }{ s }{ \intrange{n_1}{n_2} }{ \Int }{ \Delta }
}
\\]

#### CompoundOf-\\(\synth\\)

\\[
\infer{
\dots \relsp{ \Gamma }{ s }{ p_n }{ A_n }{ \Delta_n }
}{
\relsp{ \Gamma }{ s }{ \compound{\dots, k_n: p_n} }{ \Compound{\dots, k_n: A_n} }{ \dots, \Delta_n }
}
\\]

#### CompoundOf-\\(\check\\)

\\[
\infer{
\dots \relcp{ \Gamma }{ s }{ p_n }{ A_n }{ \Delta_n }
}{
\relcp{ \Gamma }{ s }{ \compound{\dots, k_n: p_n} }{ \Compound{\dots, k_n: A_n} }{ \dots, \Delta_n }
}
\\]

#### CodeOf-\\(\synth\\)

\\[
\infer{
\relsp{ \Gamma }{ s }{ p }{ \Code{A} }{ \Delta }
}{
\relsp{ \Gamma }{ s+1 }{ \code{p} }{ A }{ \Delta }
}
\\]

#### Splice-\\(\check\\)

\\[
\infer{
\relcp{ \Gamma }{ s }{ p }{ \Code{A} }{ \Delta }
}{
\relcp{ \Gamma }{ s+1 }{ \code{p} }{ A }{ \Delta }
}
\\]

#### Var-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relf{ A }{ \Type{\tau} }
}{
\relsp{ \Gamma }{ s }{ x }{ A }{ x :_s A }
}
\\]

#### Var-\\(\check\\)

\\[
\infer{}{
\relcp{ \Gamma }{ s }{ x }{ A }{ x :_s A }
}
\\]

#### Drop-\\(\synth\\)

\\[
\infer{
\relf{ \tau }{ \Tag } \\\\
\relf{ A }{ \Type{\tau} }
}{
\relsp{ \Gamma }{ s }{ \drop }{ A }{ \cdot }
}
\\]

#### Drop-\\(\check\\)

\\[
\infer{}{
\relcp{ \Gamma }{ s }{ \drop }{ A }{ \cdot }
}
\\]

#### Sub-\\(\check\\)

\\[
\infer{
\relsp{ \Gamma }{ s }{ p }{ A }{ \Delta } \\\\
\relsub{ \Gamma }{ A }{ B }
}{
\relcp{ \Gamma }{ s }{ p }{ B }{ \Delta }
}
\\]
