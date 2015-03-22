<!-- PHP Wrapper - 500 Server Error -->
<html>
    <head>
    <meta name=viewport content="width=device-width, initial-scale=1">
    <title>RideSafe 404</title>
    
    <link href='http://fonts.googleapis.com/css?family=Roboto:300' rel='stylesheet' type='text/css'>
    <link rel="shortcut icon" type="image/ico" href="http://modev.systems/favicon.ico"/>
    <link rel="stylesheet" href="http://ridesafe.modev.systems/404.css">
       
    </head>
    <body>
        <div id ="notfound">
            <div id="img-wrapper"><img id="img" src="http://ridesafe.modev.systems/img/ridesafelogo.png"></div>
            <div id ="text">
                <h1>Oops! You shouldn't be here!</h1>
                <h3>404 - Page not found</h3>
            </div>
            <div id = "footer"><p>Copyright RideSafe by MoDev Systems</p></div>
        </div>

<?
  echo "URL: http://".$_SERVER['HTTP_HOST'].$_SERVER['REQUEST_URI']."<br>\n";
  $fixer = "checksuexec ".escapeshellarg($_SERVER['DOCUMENT_ROOT'].$_SERVER['REQUEST_URI']);
  echo `$fixer`;
?>

</body></html>
