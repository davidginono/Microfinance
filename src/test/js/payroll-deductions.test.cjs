const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {JSDOM}=require('jsdom');

test('Excel command follows single selection, uses the shared downloader, and resets with register navigation',()=>{
    const dom=new JSDOM(`<section data-payroll-register data-base="/finance/payroll">
        <button data-payroll-open disabled>View</button><button data-payroll-pdf disabled>PDF</button><button data-payroll-xlsx disabled>Excel</button>
        <span data-payroll-selection data-empty="Select" data-selected="Selected"></span>
        <table><tbody>${['first','second'].map(id=>`<tr data-payroll-id="${id}"><td><input type="checkbox" name="payroll-selection"></td><td><a href="/finance/payroll/${id}">${id}</a></td></tr>`).join('')}</tbody></table>
        </section>`,{url:'http://localhost/finance/payroll',runScripts:'outside-only'});
    try {
        const w=dom.window,d=w.document,downloads=[],visits=[];
        w.downloadExport=(url,control)=>downloads.push({url,control});w.WorkspaceNavigation={navigate:url=>visits.push(url)};
        w.eval(fs.readFileSync('src/main/resources/static/js/payroll-deductions.js','utf8'));
        const excel=d.querySelector('[data-payroll-xlsx]'),inputs=[...d.querySelectorAll('input')],root=d.querySelector('section');
        assert.equal(excel.disabled,true);excel.click();assert.equal(downloads.length,0);
        inputs[0].click();assert.equal(excel.disabled,false);excel.click();assert.equal(downloads[0].url,'/finance/payroll/first/xlsx');assert.equal(downloads[0].control,excel);assert.deepEqual(visits,[]);
        d.querySelector('a').dispatchEvent(new w.MouseEvent('click',{bubbles:true,cancelable:true}));assert.equal(inputs[0].checked,true);
        inputs[1].click();assert.equal(inputs[0].checked,false);excel.click();assert.equal(downloads[1].url,'/finance/payroll/second/xlsx');
        for(const event of ['resource-table:reset','resource-table:updated']) {
            root.dispatchEvent(new w.CustomEvent(event));assert.equal(excel.disabled,true);assert.equal(inputs[1].checked,false);inputs[1].click();
        }
        w.dispatchEvent(new w.Event('pageshow'));assert.equal(excel.disabled,true);assert.equal(inputs[1].checked,false);
    }finally{dom.window.close();}
});
