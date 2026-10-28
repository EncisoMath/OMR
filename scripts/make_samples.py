#!/usr/bin/env python3
"""Create two A4 PDFs using the exact bubble positions in SheetPdf.kt."""
from pathlib import Path
from argparse import ArgumentParser
from reportlab.pdfgen.canvas import Canvas
from reportlab.lib.colors import HexColor

W, H = 595, 842
MARKERS = [(35, 35), (560, 35), (35, 807), (560, 807), (35, 421), (560, 421)]

def create(path, completed):
    c = Canvas(str(path), pagesize=(W, H))
    def box(x,y,w,h,color):
        c.setFillColor(HexColor(color))
        c.rect(x,H-y-h,w,h,stroke=0,fill=1)
    def line(x,y,x2,y2,color="#D7DCE2"):
        c.setStrokeColor(HexColor(color))
        c.setLineWidth(.8)
        c.line(x,H-y,x2,H-y2)
    def t(x,y,v,size=10,bold=False,color="#17212F"):
        c.setFillColor(HexColor(color))
        c.setFont("Helvetica-Bold" if bold else "Helvetica",size)
        c.drawString(x,H-y,v)
    for x,y in MARKERS:
        box(x-12,y-12,24,24,"#000000")
        box(x-4.5,y-4.5,9,9,"#FFFFFF")
    box(55,43,485,34,"#152332")
    t(66,66,"ROQUE OMR",18,True,"#FFFFFF")
    t(405,65,"HOJA DE RESPUESTAS",8,True,"#BFF9EA")
    t(55,102,"PRUEBA",8,True,"#455567")
    t(105,102,"DEMO 40 PREGUNTAS",10,True)
    t(55,126,"ESTUDIANTE",8,True,"#455567")
    line(135,129,390,129)
    t(399,126,"ID",8,True,"#455567")
    line(417,129,538,129)
    t(55,155,"Rellena completamente un solo circulo por pregunta. Usa lapiz o tinta oscura.",9)
    t(55,169,"No marques las seis guias cuadradas de calibracion.",9)
    line(55,180,540,180,"#152332")
    for col in range(2):
        left=44+col*253
        t(left+7,194,"N.",8,True,"#455567")
        for opt in range(4):
            t(left+44+21*opt,194,"ABCD"[opt],8,True,"#455567")
        line(left+4,200,left+246,200)
    for q in range(40):
        col,row=divmod(q,20)
        left=44+col*253
        y=212+25*row
        t(left+7,y+3,f"{q+1:02d}",8.7,True,"#334155")
        for opt in range(4):
            x=left+47+21*opt
            c.setStrokeColor(HexColor("#465365"))
            c.setLineWidth(1)
            c.circle(x,H-y,5,stroke=1,fill=0)
            if completed and opt==q%4:
                c.setFillColor(HexColor("#152332"))
                c.circle(x,H-y,4.4,stroke=0,fill=1)
    line(55,778,540,778)
    t(55,795,"ROQUE OMR / FORMATO A4 / v1.2",7.2,True,"#64748B")
    t(55,820,"Conserva los margenes completos para una lectura correcta.",7.2,False,"#64748B")
    c.showPage()
    c.save()

def main():
    parser=ArgumentParser()
    parser.add_argument("--out-dir",default="dist")
    args=parser.parse_args()
    out=Path(args.out_dir)
    out.mkdir(parents=True,exist_ok=True)
    create(out/"RoqueOMR_Hoja_Muestra_40_Blanco.pdf",False)
    create(out/"RoqueOMR_Hoja_Muestra_40_Resuelta.pdf",True)
    print("Generated two A4 sheets in",out.resolve())

if __name__=="__main__":
    main()
