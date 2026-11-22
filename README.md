# TV Aberta — versão Android TV / Mi Box

Mesma base do app de celular (canais, fontes, guia de programação, desativar/excluir), com tela feita para **controle remoto**.
Pacote `com.willian.tvaberta.tv` (pode ficar instalado junto com o app de celular). Android 8.0 ou superior, Mi Box S e similares.

## Como gerar o APK (só pelo navegador, no GitHub)
1. Crie um repositório novo (por exemplo `tvaberta-tv`) e envie **todo o conteúdo** desta pasta (Add file > Upload files). A pasta `.github/workflows` precisa subir junto; se o upload não mostrar a pasta oculta, crie o arquivo `.github/workflows/build-apk.yml` com Add file > Create new file e cole o conteúdo.
2. Aba **Actions** > "Gerar APK" > Run workflow (ou aguarde o push).
3. Ao terminar, em **Artifacts** baixe **TvAbertaTV** (dentro vem `TvAbertaTV.apk`).

## Como instalar na TV / Mi Box
- Coloque o APK num pendrive e instale com um gerenciador de arquivos (ative "Fontes desconhecidas"), ou use o app "Send Files to TV" / "Downloader" para baixar pelo link.

## Controle remoto
**Menu lateral e telas**
- Setas movem o foco (borda branca); **OK** abre/aciona; **Voltar** sobe um nível.
- **Segurar OK** (ou tecla **Menu**) sobre um canal: favoritar, renomear, desativar, excluir.
- Passar pelo menu lateral troca a tela; **→** entra no conteúdo.

**Assistindo**
- **▲ / ▼** (ou CH+ / CH-): próximo / canal anterior.
- **OK**: abre o menu (anterior, próximo, pausar, favoritar, mudo, tela, desativar, excluir). **Menu** abre e fecha.
- **◀ / ▶**: em filmes e séries voltam/avançam 10 s; em canais ao vivo mostram as informações.
- **Play/Pause**, **Next/Previous** do controle funcionam. **Voltar** fecha o menu e, depois, o player.
- Aparecem o nome do programa no ar e o seguinte (guia).

## Observações
- Digitar texto (busca, links) na TV é lento; o app aceita o teclado da TV ou a voz do controle quando houver.
- Código entregue sem compilação local; se o Actions ficar vermelho, mande as linhas `e: ` do log.
