# Advenced Vein Mining para Hytale

Compatibilidade desta versão: **Hytale 0.7.5**, canal **release**, com **Java 25**. O plugin é compilado contra o `HytaleServer.jar` dessa versão e declara `ServerVersion: "=0.7.5"` no manifesto. Outras versões do jogo precisam de validação antes de serem consideradas compatíveis.

Referência da API utilizada: revisão `4f7cd77adb4da3434c247b6d2acb4486c391e976`, extraída do manifesto do servidor instalado.

Versão 0.4.9: `/veinminer` abre o menu de configurações pessoais. Os atalhos `/advancedveinminer`, `/advancedveinmining` e `/veinmining` abrem o mesmo menu. Por padrão, segure a ação **agachar** ao quebrar um minério com uma picareta. Sem agachar, apenas o bloco minerado normalmente é quebrado.

O menu tem duas abas, **Blocos** e **Ferramentas**, com grades de cinco colunas, prévias nativas dos itens, nomes, IDs e caixas de seleção. O menu cria 30 cartões inicialmente e não tem botão de carregar mais. Foi restaurado o comportamento da 0.4.4: acrescenta outro lote de até 30 quando o cursor passa sobre os cartões das duas últimas linhas carregadas. Mantenha o mouse sobre a grade ao rolar. Ao arrastar a barra lateral, volte com o cursor às últimas linhas para carregar mais. O carregamento contínuo por tempo foi removido. O total de resultados aparece junto ao título da aba, como BLOCOS AFETADOS (2183). Foram removidos o contador inferior e a instrução abaixo da lista. A busca continua abrangendo o catálogo inteiro, incluindo itens ainda não exibidos. Clique na prévia ou na caixa para marcar. A seleção atualiza sem reconstruir a grade, conservando a posição da rolagem (o filtro de marcados pode reorganizar a lista).

A busca espera 200 ms após a última alteração antes de atualizar a grade, cancelando consultas pendentes quando a tela muda ou fecha. O catálogo de ferramentas só é preparado ao abrir essa aba. A busca considera o nome traduzido no idioma do jogador e o ID, ignora acentos e maiúsculas e permite várias palavras (por exemplo, `cobre pedra`). O filtro **Somente marcados** ajuda a revisar cada lista. Trocar de aba preserva ambas as seleções e limpa a busca. As duas abas são aplicadas juntas ao salvar.

A aba de blocos oferece os blocos com item e configuração de quebra dos assets carregados. A aba de ferramentas oferece itens com configuração de ferramenta, inclusive de outros mods. O padrão mantém os minérios e picaretas das versões anteriores. Escolher um machado ou outra ferramenta na nova aba realmente permite usar essa ferramenta no processamento de mineração; desmarcá-la impede o uso. O jogo ainda decide se a ferramenta consegue danificar o bloco.

A mineração tenta quebrar até 31 blocos extras do **mesmo ID** ligados pelas faces, arestas ou cantos (32 contando o original). Selecionar vários tipos os habilita individualmente; não mistura variantes, como cobre em pedra e cobre em arenito, na mesma operação. É obrigatório usar uma ferramenta marcada na aba Ferramentas.

A distância pode ser digitada no campo numérico ou ajustada pelos botões +/−. É um raio em linha reta a partir do bloco original, incluindo as três coordenadas. Vai de 1 a 32, com padrão 8. É independente do limite de quantidade de 32 blocos. Por exemplo, com raio 1, vizinhos diagonais ficam fora do alcance por estarem a mais de um bloco de distância. O servidor valida os limites também ao salvar, incluindo quando o campo ainda está em foco.

**Salvar e fechar** aplica as alterações e grava um arquivo por UUID em `players` na pasta original `Pedro_VeinMiner` (mantida para preservar as preferências após a mudança de nome). ESC/Cancelar descarta o rascunho das duas abas. **Padrões** restaura somente a lista da aba atual (minérios em Blocos, picaretas em Ferramentas), sem alterar alcance ou ativação; só aplica ao salvar. **Limpar** desmarca a lista da aba atual. Salvar uma lista de blocos ou ferramentas vazia desativa a mineração extra para o jogador.

Os arquivos de versões anteriores são compatíveis: seus blocos, alcance e ativação são preservados. Quando o campo `Tools` ainda não existe, as picaretas padrão são usadas. Uma lista de ferramentas salva vazia permanece vazia após reiniciar.

## Tecla de ativação

O padrão é a ação **Agachar**; as alternativas do menu são **Caminhar** e **Correr**. A interface usa `HotkeyLabel` nativo para mostrar a tecla correspondente entre parênteses, como **Agachar (CTRL)**, respeitando os controles configurados no cliente. O plugin acompanha o estado dessas ações informado pelo jogo, não registra uma tecla de teclado arbitrária. Para o comportamento de manter pressionado, configure agachar no modo **segurar**, não alternar. Agachamento forçado pelo ambiente não ativa o mod. Caminhar e correr dependem do estado de movimento e não funcionam como modificadores parados.

Só acessa seções de chunks já carregadas. As quebras extras usam o fluxo de dano do jogo, com uma batida acelerada por bloco, drops e desgaste calculados pelo servidor. Interrompe se a ferramenta estiver quebrada, não estiver permitida ou mudar de tipo durante a operação. O custo é o de uma batida por bloco extra, não o total de batidas que seriam necessárias minerando manualmente.

Os eventos canceláveis do jogo continuam sendo disparados. A busca só atravessa blocos extras efetivamente quebrados; uma quebra recusada barra aquele caminho. Integração com plugins específicos de proteção ainda precisa de teste no jogo.

## Compilar no Windows

Requer JDK 25 e uma instalação do Hytale. O Gradle Wrapper baixa a versão fixa do Gradle na primeira execução.

O caminho padrão é `D:/Games/Hytale`. Para alterar, copie `local.properties.example` para `local.properties` e ajuste `hytale.home`. Também é possível definir `HYTALE_HOME` ou passar `-PhytaleHome=...`.

```powershell
.\gradlew.bat build
```

Resultado: `build/libs/advenced-vein-mining-0.4.9.jar`, incluindo os assets da interface. O servidor do jogo é usado apenas para compilação e não é incluído no plugin. O build executa testes de busca, raio, ativação, ferramentas permitidas/bloqueadas, isolamento por jogador, migração, validação e persistência sem abrir o jogo.

## Testar no jogo

1. Feche o mundo de teste antes de instalar o plugin.
2. Retire as versões anteriores do VeinMiner / Advenced Vein Mining da pasta de mods e coloque `advenced-vein-mining-0.4.9.jar` em `D:\Games\Hytale\UserData\Mods`. Mantenha apenas uma versão instalada.
3. Abra um mundo de teste, habilite o mod nas configurações do mundo se necessário e execute `/veinminer`. Deve abrir o menu.
4. Role a grade, busque pelo nome ou ID, marque um bloco e confira sua prévia. Troque para Ferramentas e altere uma ferramenta. Digite o alcance e clique em **Salvar e fechar** sem sair do campo. Abra novamente e confira os valores. ESC/Cancelar não deve aplicar mudanças.
5. Selecione **Agachar** e salve. Segure agachar enquanto minera cobre ou ferro com uma picareta. Ao minerar sem agachar, os blocos vizinhos devem permanecer.
6. Desmarque a ferramenta usada, salve e confirme que ela não inicia mais a mineração de veios. Marque-a novamente e confirme que volta a funcionar. Confira que os valores permanecem após reiniciar o mundo e que a tecla entre parênteses corresponde aos seus controles.

Para servidor dedicado, use a pasta `mods` do próprio servidor. Reinicie o mundo/servidor após substituir o `.jar`. A 0.4.9 mantém o carregamento por cursor da 0.4.4, mantendo a correção que evita eventos MouseEntered nas caixas de seleção. Compilação e testes automatizados passaram.

## Validação no jogo

- Conferir drops sem duplicação e desgaste da picareta por bloco extra em modo aventura.
- Conferir parada quando a picareta quebra.
- Conferir limite de 32 em um veio grande e comportamento entre chunks carregados.
- Confirmar que as diagonais dentro do raio são mineradas, que blocos desmarcados não iniciam a operação e que tipos diferentes e veios separados por espaço vazio ficam intactos.
- Testar cancelamentos com o plugin de proteção usado no servidor, se houver.

## Próximas melhorias

- Configuração de quantidade por veio e opção para desligar diagonais.
- Política configurável de desgaste e balanceamento.

As releases são publicadas em [GitHub Releases](https://github.com/pedromilho/advancedveinmining/releases). Baixe apenas o `.jar` do mod; `HytaleServer.jar` e os assets originais do jogo não são distribuídos neste repositório.
