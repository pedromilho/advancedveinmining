# Advenced Vein Mining v0.4.9 — Hytale 0.7.5

Primeira release pública do mod.

## Compatibilidade

- Hytale **0.7.5**, canal **release**.
- Java **25**.
- API do servidor: revisão `4f7cd77adb4da3434c247b6d2acb4486c391e976`.
- Compilado contra o servidor dessa versão; o manifesto exige `ServerVersion: "=0.7.5"`.
- Outras versões do Hytale ainda não foram validadas.

## Funcionalidades

- Mineração de até 32 blocos iguais conectados, incluindo diagonais.
- Ativação padrão pela ação de agachar, com raio configurável de 1 a 32 blocos.
- Menu com prévias dos blocos e ferramentas, busca e seleção individual.
- Preferências persistidas por jogador.
- Comandos equivalentes: `/veinminer`, `/advancedveinminer`, `/advancedveinmining` e `/veinmining`.
- Catálogo inclui blocos e ferramentas de outros mods que exponham as configurações de item, quebra e ferramenta esperadas pela API; essa integração ainda precisa de testes com outros mods.

## Instalação

1. Feche o mundo ou pare o servidor.
2. Retire versões anteriores do VeinMiner / Advenced Vein Mining da pasta de mods.
3. Coloque `advenced-vein-mining-0.4.9.jar` na pasta `Mods` do UserData ou na pasta `mods` do servidor dedicado.
4. Reabra o mundo, habilite o mod se necessário e execute `/veinminer`.

As configurações existentes continuam em `Pedro_VeinMiner/players`.

## Limitações conhecidas

- O menu carrega 30 itens inicialmente. O próximo lote é acionado quando o cursor passa pelos cartões das últimas linhas. Ao arrastar a barra lateral, é necessário voltar o cursor à grade para carregar outro lote.
- Configure agachar no modo **segurar** nos controles do jogo. Caminhar e correr exigem movimento.
- O custo dos blocos extras equivale a uma batida por bloco, com drops e desgaste calculados pelo jogo.
- Compatibilidade com plugins específicos de proteção e com outros mods ainda requer validação no jogo.

## Validação

Build com JDK 25 e Gradle Wrapper. Testes automatizados de busca de veios, raio, ativação, seleção de ferramentas, persistência, migração e catálogo com 5.003 itens. As validações de interface e mineração feitas durante o desenvolvimento não cobrem todas as combinações de mods ou ferramentas.
