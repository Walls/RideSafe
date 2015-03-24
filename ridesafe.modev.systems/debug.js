$(document).ready(function(){
    var map;  
    var ref;
    var token;
    $('#status').append("Loading<br/>");
    function goToError(){
        
        setTimeout(function(){
            $('body').addClass('fadeout');
            var errorId = document.getElementById("error");
            errorId.style.display = "block";

            var mapId = document.getElementById("map");
            mapId.style.display = "none"; 
        }, 3000);

    }
    
    function getToken(urlQS){
        $.ajax({
            url: "http://ridesafe.modev.systems/cgi-bin/makeAuthToken.py",
            type: "post",
            datatype: "text",
            data: {"URL": urlQS},
            success: function(scriptToken){
                if(scriptToken.length=0){
                    goToError();
                }
                token=String(scriptToken);
                $('#status').append("Connection established.<br/>");

            },
            error: function(error){
                    $('#status').append("Authentication Error<br/>");
                goToError();
            }
        })
        
    }
    
    function initialize(){
        // Get a reference to our posts
        var myLatlng = null;
        var prevLatlng = null
        var marker = null;
        var firstMarker = null;
        
            map = new google.maps.Map(document.getElementById('map-canvas'), {center: myLatlng, zoom: 16});
            $('#status').append("Waiting on coordinate data.<br/>");
            ref.on("child_added", function(snapshot) {
            var gpsCord = snapshot.val();
            //create Latlng maps var using gpsCord String
            var resStr = gpsCord.split(" ");
            var lat=Number(resStr[0]);
            var lng=Number(resStr[1]);
            
           
            //connect the dots
            prevLatlng = myLatlng;
            myLatlng = new google.maps.LatLng(lat,lng);
            
            //set begining point
            if(prevLatlng==null && myLatlng!=null){
                    $('#status').append("Waiting on additional data.<br/>");
                    firstMarker = new google.maps.Marker({
                    position: myLatlng,
                    title:"Starting Position",
                    icon: 'img/start_marker.png'
                });
                firstMarker.setMap(map);
                map.setCenter(myLatlng);
            }
                
            // Add to poly line and update/re-center Map
            if(prevLatlng!=null&&myLatlng!=null&&prevLatlng&&prevLatlng!=myLatlng){
                
                var flightPath = new google.maps.Polyline({
                path: [prevLatlng,myLatlng],
                geodesic: true,
                strokeColor: '#D916B0',
                strokeOpacity: .75,
                strokeWeight: 10
              });
                //remove old marker and add a new one for the current local
                
                if(marker!=null){
                     marker.setMap(null);
                }
                
                marker = new google.maps.Marker({
                    position: myLatlng,
                    title:"Last Recorded Position",
                    icon: 'img/end_marker.png'
                });
                marker.setMap(map);
                
               
                
                flightPath.setMap(map);
                map.setCenter(myLatlng);
            }//end null if

            });
        
        }//end init
    //get tokens from makeAuthToken.py
     
    //Create firebase reference, start Auth
    function construct(){
            ref = new Firebase("https://ridesafe.firebaseio.com/"+queryString);

            if(String(token)=="undefined"){
                goToError();
                $('#status').append("Authentication Error. Please attempt to reload.<br/>");
             }//token response took too long
            else {
                ref.authWithCustomToken(token, function(error, authData) { 
                 if (error) {
                     $('#status').append("Authentication Error.<br/>");
                     goToError();
                  } else {
                       
                           
                    ref.once('value', function(snapshot) {
                    if (snapshot.val() === null) {
                        $('#status').append("Data missing<br/>. Please attempt to reload.<br/>");
                        goToError();
                    } 
                    else {
                        //if we got a good child, create a map and load it up with points, fadeout of loading screen    
                        $('#status').append("Connection to data established.<br/>");
                           google.maps.event.addDomListener(window, 'load', initialize());

                           google.maps.event.addListenerOnce(map, 'tilesloaded', function(){$('body').addClass('fadeout'); });
                    }//end else

                    });//end firebae ref

                  }//end auth else
                }, {
                  remember: "sessionOnly"//delete user at browser close
                });
                
            }//end token else     
    }//end construct
    
        //queryStrig extraction and firebase child check
        var queryString = window.location.search;
        queryString = queryString.substring(1);
        if(queryString.length == 0){
             $('#status').append("Invalid map URL.<br/>");
            goToError();
        }
        else{
            //get tokens from makeAuthToken.py, then go to constructor
            $('#status').append("Attempting to authenticate and create map.<br/>");
            getToken(queryString);
            setTimeout(function(){
                construct();
                
            }, 3000);
        }
    
});//end doc ready