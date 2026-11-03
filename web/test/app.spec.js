import { test, expect } from '@playwright/test';
async function create(page,name='Camille'){await page.goto('/');await page.getByLabel('Votre prénom ou indicatif').fill(name);await page.getByRole('button',{name:'Créer mon profil'}).click();await expect(page.getByRole('heading',{name:`Bonjour, ${name}.`})).toBeVisible();}
test('mobile onboarding, installation instructions and persistent profiles',async({page},testInfo)=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:'Installer',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('Sur l’écran d’accueil');
  await page.getByRole('button',{name:'Fermer',exact:true}).click();await page.reload();await expect(page.getByRole('heading',{name:'Bonjour, Camille.'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();expect(errors).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('home-mobile.png'),fullPage:true});
});
test('character playback loads the real audio worklet and can be cancelled',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:/Fiches des caractères/}).click();await page.locator('.char-card[data-character="K"]').click();
  await expect(page.getByRole('dialog')).toContainText('ti');await page.getByRole('button',{name:'Fermer',exact:true}).click();expect(errors).toEqual([]);
});
test('listening exercise correction updates one track and survives reload',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:/Écouter → écrire/}).first().click();await page.getByRole('button',{name:'Démarrer la série'}).click();
  await expect(page.getByLabel('Ce que vous entendez')).toBeVisible();await page.getByLabel('Ce que vous entendez').fill('KM');await page.getByRole('button',{name:'Corriger ma saisie'}).click();
  await expect(page.getByText('Votre résultat',{exact:true})).toBeVisible();await page.getByRole('button',{name:'Progrès',exact:true}).click();await expect(page.getByText('Séries terminées',{exact:true})).toBeVisible();
  const stored=await page.evaluate(()=>JSON.parse(localStorage.getItem('hermorse.profiles.v1'))[0]);expect(stored.stats.series).toBe(1);expect(stored.stats.txLevel).toBe(1);expect(errors).toEqual([]);
});
test('virtual key decodes a held pointer; import/export preserve Android profiles',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:/Saisie libre/}).first().click();const pad=page.getByRole('button',{name:'Pioche',exact:true});await pad.scrollIntoViewIfNeeded();const box=await pad.boundingBox();
  await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();await page.waitForTimeout(100);await page.mouse.up();
  await expect(page.locator('#decoded')).toHaveText('E');
  await page.getByRole('button',{name:'Changer de profil'}).click();await page.getByRole('button',{name:'Importer un profil',exact:true}).click();
  const androidProfile={format:'hermorse',version:1,name:'F4ABC',settings:{keyMode:'iambicB'},stats:{rxLevel:12,rxSpeed3:3,rxChars:{K:[8,10]}}};
  await page.locator('#import-file').setInputFiles({name:'test.hermorse',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(androidProfile))});
  await expect(page.getByRole('heading',{name:'Bonjour, F4ABC.'})).toBeVisible();await page.getByRole('button',{name:'Réglages',exact:true}).click();
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Exporter mon profil',exact:true}).click();expect((await download).suggestedFilename()).toBe('F4ABC.hermorse');expect(errors).toEqual([]);
});
test('microphone permission path starts real capture worklet and releases the track',async({page,context})=>{
  await context.grantPermissions(['microphone']);const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:'QSO',exact:true}).click();await page.getByRole('button',{name:'Écouter le poste'}).click();await expect(page.getByRole('button',{name:'Arrêter l’écoute'})).toBeVisible();
  await expect(page.locator('#qso-signal')).toHaveText(/Pas de signal|≈/);await page.getByRole('button',{name:'Arrêter l’écoute'}).click();await expect(page.getByRole('button',{name:'Écouter le poste'})).toBeVisible();expect(errors).toEqual([]);
});
test('service worker installs all assets and application reopens offline',async({page,context})=>{
  await create(page);await page.evaluate(async()=>{await navigator.serviceWorker.ready;});await page.reload();await page.waitForFunction(()=>navigator.serviceWorker.controller!==null);
  await context.setOffline(true);await page.reload();await expect(page.getByRole('heading',{name:'Bonjour, Camille.'})).toBeVisible();
  await page.getByRole('button',{name:/Fiches des caractères/}).click();await expect(page.locator('.char-card[data-character="K"]')).toBeVisible();
  const failures=[];page.on('requestfailed',r=>failures.push(r.url()));await page.locator('.char-card[data-character="K"]').click();await page.waitForTimeout(300);expect(failures).toEqual([]);
});
test('malformed import is rejected without deleting an existing profile',async({page})=>{
  await create(page);await page.locator('#import-file').setInputFiles({name:'bad.hermorse',mimeType:'application/json',buffer:Buffer.from('{"format":"other"}')});
  await expect(page.locator('#notice')).toContainText('pas un profil');await expect(page.getByRole('heading',{name:'Bonjour, Camille.'})).toBeVisible();
});
test('send exercise corrects an error, then accepts the target character',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));await create(page);
  await page.getByRole('button',{name:'Réglages',exact:true}).click();await page.getByLabel('Décodage adaptatif de la pioche').uncheck();await page.getByLabel('Vitesse fixe de la pioche (WPM)').fill('12');await page.getByLabel('Vitesse fixe de la pioche (WPM)').blur();await page.getByRole('button',{name:'Fermer',exact:true}).click();
  await page.getByRole('button',{name:/Lire → manipuler/}).first().click();await page.getByRole('button',{name:'Démarrer la série'}).click();
  const target=await page.locator('#send-target .current').textContent(),pad=page.getByRole('button',{name:'Pioche',exact:true});
  await pad.scrollIntoViewIfNeeded();const box=await pad.boundingBox();
  const mark=async ms=>{await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();await page.waitForTimeout(ms);await page.mouse.up();};
  await mark(100);await expect(page.locator('#correction-label')).toHaveText('Écoutez la correction…');await expect(page.locator('#correction-label')).toHaveText('');
  expect(await page.locator('#send-target .current').textContent()).toBe(target);
  const code={K:'-.-',M:'--'}[target];
  for(let i=0;i<code.length;i++){await mark(code[i]==='.'?100:300);if(i<code.length-1)await page.waitForTimeout(100);}
  await expect(page.locator('#send-target span').first()).toHaveClass('good');
  const stats=await page.evaluate(()=>JSON.parse(localStorage.getItem('hermorse.profiles.v1'))[0].stats);expect(stats.totalChars).toBe(2);expect(stats.errors).toBe(1);expect(errors).toEqual([]);
});
