; Atari 2600: рисует радугу на экране. Ассемблер 6502 (синтаксис DASM).
; Соберите на компьютере:  dasm game.asm -f3 -ogame.bin  и откройте game.bin в эмуляторе Stella.
    processor 6502

VSYNC  = $00        ; регистры видеочипа TIA
VBLANK = $01
WSYNC  = $02
COLUBK = $09        ; цвет фона

    seg code
    org $F000       ; картридж начинается с адреса $F000

Reset:
    sei             ; запретить прерывания
    cld             ; обычная (не десятичная) арифметика
    ldx #$FF
    txs             ; стек
    lda #0
Clear:
    sta 0,x         ; обнулить память и регистры
    dex
    bne Clear

Frame:              ; один кадр телевизора
    lda #2
    sta VSYNC       ; 3 строки синхроимпульса
    sta WSYNC
    sta WSYNC
    sta WSYNC
    lda #0
    sta VSYNC
    ldx #37         ; 37 строк паузы сверху
VBlank:
    sta WSYNC
    dex
    bne VBlank
    lda #0
    sta VBLANK
    ldx #192        ; 192 видимые строки: номер строки = цвет
Kernel:
    stx COLUBK
    sta WSYNC
    dex
    bne Kernel
    lda #2
    sta VBLANK
    ldx #30         ; 30 строк снизу
Overscan:
    sta WSYNC
    dex
    bne Overscan
    jmp Frame

    org $FFFC
    .word Reset     ; адрес запуска
    .word Reset
