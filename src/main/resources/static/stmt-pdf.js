// Baixar PDF do extrato.
// Monta um PDF de verdade (texto vetorial, sempre nítido) direto dos dados do extrato,
// em vez de depender do "Imprimir / PDF" do navegador do celular.
// Precisa de: app.js (state, fmtDate, dayTitle, toast) e das bibliotecas jsPDF + jsPDF-AutoTable.
(() => {
  const INK = [17, 17, 17], MUTED = [110, 110, 125], POS = [4, 120, 87], NEG = [185, 28, 28];
  const LINE = [204, 204, 204], DAYBG = [238, 238, 243];

  const money = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }).replace(/\u00a0/g, ' ');
  // A fonte padrão do PDF não desenha emoji nem alguns símbolos: tira o que ela não sabe desenhar.
  const clean = (s) => String(s ?? '').replace(/[\u2192\u279c]/g, '>').replace(/\u2212/g, '-').replace(/[^\u0020-\u007e\u00a1-\u00ff\u2013\u2014\u2018-\u201d\u2022\u20ac]/g, '').replace(/\s+/g, ' ').trim();
  const signed = (e) => (e.internal ? '' : e.delta >= 0 ? '+ ' : '- ') + money(e.amount);
  const tone = (e) => (e.internal ? INK : e.delta >= 0 ? POS : NEG);

  function entryRow(e, consolidated, withDate) {
    const sub = [e.label || e.categoryName, consolidated && !e.internal ? e.accountName : null, withDate ? fmtDate(e.date) : null]
      .filter(Boolean).map(clean).filter(Boolean).join(' · ');
    return [
      clean(e.description),
      { content: sub, styles: { textColor: MUTED } },
      { content: signed(e), styles: { halign: 'right', fontStyle: 'bold', textColor: tone(e) } },
    ];
  }

  function buildPdf() {
    const s = state.stmt;
    if (!s) { toast('Abra o extrato primeiro.', true); return; }
    const lib = window.jspdf;
    if (!lib || !lib.jsPDF) { toast('Não consegui carregar o gerador de PDF. Confira a internet e tente de novo.', true); return; }

    const doc = new lib.jsPDF({ unit: 'mm', format: 'a4' });
    if (typeof doc.autoTable !== 'function') { toast('Não consegui carregar o gerador de tabelas do PDF.', true); return; }
    const W = doc.internal.pageSize.getWidth(), H = doc.internal.pageSize.getHeight(), M = 14;

    // mesmas regras da tela: filtro (tudo / entradas / saídas) e dias do mais novo para o mais antigo
    const f = state.stFilter;
    const keep = (e) => f === 'all' || (f === 'in' && e.delta > 0) || (f === 'out' && e.delta < 0);
    const days = [...s.days].reverse().map((d) => ({ ...d, entries: d.entries.filter(keep).reverse() })).filter((d) => d.entries.length);
    const upcoming = (s.scheduled || []).filter(keep);

    // ---- cabeçalho
    doc.setFont('helvetica', 'normal'); doc.setFontSize(9); doc.setTextColor(...MUTED);
    doc.text('Extrato', M, 16);
    doc.setFont('helvetica', 'bold'); doc.setFontSize(16); doc.setTextColor(...INK);
    doc.text(clean(s.accountName) || 'Total', M, 23);
    doc.setFont('helvetica', 'normal'); doc.setFontSize(9); doc.setTextColor(...MUTED);
    doc.text(`${fmtDate(s.from)} a ${fmtDate(s.to)}${f === 'in' ? '  (só entradas)' : f === 'out' ? '  (só saídas)' : ''}`, M, 29);
    doc.setFont('helvetica', 'bold'); doc.setTextColor(...INK);
    doc.text('Finanças', W - M, 16, { align: 'right' });
    doc.setFont('helvetica', 'normal'); doc.setTextColor(...MUTED);
    doc.text('Gerado em ' + new Date(s.generatedAt).toLocaleString('pt-BR'), W - M, 22, { align: 'right' });

    // ---- resumo
    let y = 38;
    [['Saldo anterior', money(s.openingBalance), INK, 'normal'],
     ['Entradas', '+ ' + money(s.totalIn), POS, 'bold'],
     ['Saídas', '- ' + money(s.totalOut), NEG, 'bold'],
     ['Saldo final', money(s.closingBalance), INK, 'bold']].forEach(([label, value, color, style]) => {
      doc.setFont('helvetica', 'normal'); doc.setFontSize(10); doc.setTextColor(...MUTED);
      doc.text(label, M, y);
      doc.setFont('helvetica', style); doc.setTextColor(...color);
      doc.text(value, W - M, y, { align: 'right' });
      y += 6;
    });
    doc.setDrawColor(...LINE); doc.line(M, y - 2, W - M, y - 2);

    // ---- lançamentos
    const body = [];
    days.forEach((d) => {
      body.push([{ content: clean(dayTitle(d.date)), colSpan: 3, styles: { fillColor: DAYBG, fontStyle: 'bold' } }]);
      d.entries.forEach((e) => body.push(entryRow(e, s.consolidated, false)));
      if (f === 'all') {
        body.push([
          { content: 'Saldo do dia', colSpan: 2, styles: { halign: 'right', textColor: MUTED } },
          { content: money(d.closingBalance), styles: { halign: 'right', fontStyle: 'bold' } },
        ]);
      }
    });
    if (!days.length) body.push([{ content: 'Nenhum lançamento no período.', colSpan: 3, styles: { halign: 'center', textColor: MUTED } }]);
    if (upcoming.length) {
      body.push([{ content: 'Agendados (ainda não entram no saldo)', colSpan: 3, styles: { fillColor: [255, 244, 214], fontStyle: 'bold' } }]);
      upcoming.forEach((e) => body.push(entryRow(e, s.consolidated, true)));
    }

    doc.autoTable({
      startY: y + 2,
      margin: { left: M, right: M, bottom: 16 },
      head: [['Descrição', 'Categoria / conta', 'Valor']],
      body,
      theme: 'grid',
      styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, textColor: INK, lineColor: LINE, lineWidth: 0.1 },
      headStyles: { fillColor: [30, 30, 40], textColor: 255 },
      columnStyles: { 1: { cellWidth: 55 }, 2: { cellWidth: 34 } },
      didParseCell: (h) => { if (h.section === 'head' && h.column.index === 2) h.cell.styles.halign = 'right'; },
    });

    // ---- rodapé com número de página
    const pages = doc.getNumberOfPages();
    for (let i = 1; i <= pages; i++) {
      doc.setPage(i); doc.setFont('helvetica', 'normal'); doc.setFontSize(8); doc.setTextColor(...MUTED);
      doc.text(`Página ${i} de ${pages}`, W / 2, H - 8, { align: 'center' });
    }

    const nome = (s.accountName || 'total').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'total';
    doc.save(`extrato-${nome}-${s.from}_${s.to}.pdf`);
  }

  document.getElementById('stPdf').onclick = () => {
    try { buildPdf(); } catch (err) { console.error(err); toast('Não consegui gerar o PDF.', true); }
  };
})();
