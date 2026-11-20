# TV Aberta

Aplicativo Android para assistir a canais de TV abertos e gratuitos.
Versão para **celular** (Android 8.0 ou superior). A versão para Android TV / Mi Box virá depois.

- **Pacote:** `com.willian.tvaberta`
- **Compatibilidade:** minSdk 26 (Android 8.0), targetSdk 34
- **Compilação:** só pelo GitHub Actions, usando a interface web do GitHub (sem terminal)

## O que o app faz

- Já vem com 4 fontes cadastradas (Configurações > Fontes): **IPTV-org – Brasil** (ligada), **CatTV Playlist – Brasil** (desligada: mistura canais públicos com marcas de TV paga retransmitidas por terceiros; ligue por sua conta), **PublicDomainM3U – Filmes** (filmes de domínio público, ligada) e **PublicDomainM3U – Séries** (ligada). Dá para adicionar outros países, os canais em português de todos os países ou uma lista própria por link.
- Filmes e séries têm barra de progresso para avançar e voltar; canais ao vivo não.
- Player com Media3 (ExoPlayer): HLS, DASH e outros; tenta links de reserva e reconecta sozinho quando o canal cai.
- **Desativar** (esconde, mas dá para reativar) e **excluir** (some de vez e não volta nas atualizações; dá para restaurar em Gerenciar > Excluídos), um a um pelo menu ⋮ ou em lote na aba Gerenciar. Excluir e desativar têm "Desfazer".
- Favoritos, busca, filtro por categoria, ordenação (nome, mais assistidos, recentes), grade ou lista.
- Adicionar canal manualmente e importar arquivo M3U do aparelho.
- Player em tela cheia: trocar de canal (botões ou deslizar), proporção da imagem, girar tela, imagem em imagem (PiP), mudo.
- Mais de 25 configurações (aparência, reprodução, canais, fontes, dados e cache).

## Como gerar o APK (só pelo site do GitHub)

1. Crie um repositório novo no GitHub (por exemplo `tv-aberta`), na branch `main`.
2. Descompacte o ZIP e envie **todos** os arquivos e pastas para o repositório (Add file > Upload files). Atenção: a pasta `.github` é oculta e às vezes o navegador não a envia ao arrastar. Se a aba **Actions** não mostrar o fluxo "Gerar APK", crie o arquivo pela opção **Add file > Create new file**, digitando `.github/workflows/build-apk.yml` no nome, e cole o conteúdo do arquivo do ZIP.
3. Abra a aba **Actions**. O fluxo **Gerar APK** roda sozinho a cada envio (ou clique em Run workflow).
4. Quando ficar verde, abra a execução e baixe o artefato **TvAberta-apk**. Dentro vem o `.apk`; instale no celular.
5. Se ficar vermelho, copie as linhas que começam com `e: ` (erro de Kotlin), `What went wrong:` ou `Could not resolve` e envie para correção.

## Assinatura (importante)

O arquivo `app/tvaberta.jks` é a chave fixa do app. **Nunca troque nem apague.** Com ela, cada novo APK instala por cima do anterior sem perder dados. O `versionCode` sobe sozinho a cada execução do Actions.

## Estrutura

```
app/src/main/java/com/willian/tvaberta/
  App.kt, MainActivity.kt, MainViewModel.kt
  data/    Models, SettingsStore, M3uParser, Repo
  player/  PlayerController (ExoPlayer)
  ui/      Theme, Util, Components, ChannelsScreen, ManageScreen,
           SettingsScreen, PlayerScreen, AppRoot
```

## Aviso

O app é só um reprodutor: não hospeda nem transmite canais. Use apenas fontes legais. Listas que oferecem canais pagos de graça costumam ser pirataria.
